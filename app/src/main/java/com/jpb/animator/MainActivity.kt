package com.jpb.animator

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jpb.animator.ui.components.DrawingCanvas
import com.jpb.animator.ui.components.PaletteToolbar
import com.jpb.animator.ui.theme.AnimatorTheme
import com.jpb.animator.utils.AnimationFrame
import com.jpb.animator.utils.DrawingToolState
import com.jpb.animator.utils.Layer
import com.jpb.animator.utils.StyledPath
import com.jpb.animator.utils.exportAnimationNative
import com.jpb.animator.utils.renderFrameToBitmap
import com.jpb.animator.utils.swap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AnimatorTheme {
                var frames by remember { mutableStateOf(listOf(AnimationFrame())) }
                var currentFrameIndex by remember { mutableIntStateOf(0) }
                var currentLayerIndex by remember { mutableIntStateOf(0) }
                var toolState by remember { mutableStateOf(DrawingToolState()) }

                EditorScreen(
                    currentFrameIndex = currentFrameIndex,
                    currentLayerIndex = currentLayerIndex,
                    frames = frames,
                    toolState = toolState,
                    onToolStateChanged = { newToolState -> toolState = newToolState },
                    onPathAdded = { newStyledPath ->
                        val frame = frames[currentFrameIndex]
                        val updatedLayers = frame.layers.toMutableList()
                        val targetLayer = updatedLayers.getOrElse(currentLayerIndex) { updatedLayers.first() }

                        val newLayerPaths = targetLayer.paths + newStyledPath
                        updatedLayers[currentLayerIndex] = targetLayer.copy(paths = newLayerPaths)

                        frames = frames.toMutableList().apply {
                            this[currentFrameIndex] = frame.copy(layers = updatedLayers)
                        }
                    },
                    onAddFrame = {
                        frames += AnimationFrame(layers = listOf(Layer(name = "Layer 1")))
                        currentFrameIndex = frames.size - 1
                        currentLayerIndex = 0
                    },
                    onSelectFrame = { index ->
                        currentFrameIndex = index
                        currentLayerIndex = 0
                    },
                    onMoveFrame = { fromIndex, toIndex ->
                        if (toIndex in frames.indices) {
                            frames = frames.swap(fromIndex, toIndex)
                            currentFrameIndex = toIndex
                        }
                    },
                    onAddLayer = {
                        val frame = frames[currentFrameIndex]
                        val newLayerName = "Layer ${frame.layers.size + 1}"
                        val updatedLayers = frame.layers + Layer(name = newLayerName)
                        frames = frames.toMutableList().apply {
                            this[currentFrameIndex] = frame.copy(layers = updatedLayers)
                        }
                        currentLayerIndex = updatedLayers.size - 1
                    },
                    onSelectLayer = { index ->
                        currentLayerIndex = index
                    },
                    onMoveLayer = { fromIndex, toIndex ->
                        val frame = frames[currentFrameIndex]
                        if (toIndex in frame.layers.indices) {
                            val updatedLayers = frame.layers.swap(fromIndex, toIndex)
                            frames = frames.toMutableList().apply {
                                this[currentFrameIndex] = frame.copy(layers = updatedLayers)
                            }
                            currentLayerIndex = toIndex
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun EditorScreen(
    currentFrameIndex: Int,
    currentLayerIndex: Int,
    frames: List<AnimationFrame>,
    toolState: DrawingToolState,
    onToolStateChanged: (DrawingToolState) -> Unit,
    onPathAdded: (StyledPath) -> Unit,
    onAddFrame: () -> Unit,
    onSelectFrame: (Int) -> Unit,
    onMoveFrame: (Int, Int) -> Unit,
    onAddLayer: () -> Unit,
    onSelectLayer: (Int) -> Unit,
    onMoveLayer: (Int, Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.DarkGray)
            .statusBarsPadding()
    ) {
        // 1. Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("My Animation", color = Color.White, fontSize = 20.sp)
            val coroutineScope = rememberCoroutineScope()
            val context = LocalContext.current
            val createVideoLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("video/mp4")
            ) { uri: Uri? ->
                if (uri != null) {
                    Toast.makeText(context, "Exporting video...", Toast.LENGTH_SHORT).show()

                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val exportResult = exportAnimationNative(context, frames)

                            if (exportResult.first) {
                                val generatedFile = File(exportResult.second)
                                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                                    generatedFile.inputStream().use { inputStream ->
                                        inputStream.copyTo(outputStream)
                                    }
                                }

                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Video exported successfully!", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Export failed: ${exportResult.second}", Toast.LENGTH_LONG).show()
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Export failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
            var pendingFrameIndex by remember { mutableStateOf<Int?>(null) }

            val saveSingleFrameLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("image/png")
            ) { uri: Uri? ->
                val indexToSave = pendingFrameIndex
                if (uri != null && indexToSave != null) {
                    val frameToSave = frames.getOrNull(indexToSave)
                    if (frameToSave != null) {
                        Toast.makeText(context, "Saving frame...", Toast.LENGTH_SHORT).show()

                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                                    val bitmap = renderFrameToBitmap(frameToSave)
                                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                                }
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Frame successfully saved!", Toast.LENGTH_LONG).show()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Failed to save frame: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                }
                pendingFrameIndex = null
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    pendingFrameIndex = currentFrameIndex
                    saveSingleFrameLauncher.launch("Frame_${currentFrameIndex + 1}.png")
                }) {
                    Text("Save Frame")
                }

                Button(onClick = {
                    Toast.makeText(context, "Rendering video...", Toast.LENGTH_SHORT).show()
                    coroutineScope.launch {
                        createVideoLauncher.launch("MyAnimation.mp4")
                    }
                }) {
                    Text("Export")
                }
            }
        }

        // Layer selection mini-bar with re-ordering controls
        val currentLayers = frames.getOrNull(currentFrameIndex)?.layers ?: emptyList()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Layers:", color = Color.White, fontSize = 14.sp)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(currentLayers.size) { layerIndex ->
                    val isLayerSelected = layerIndex == currentLayerIndex
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .background(
                                if (isLayerSelected) Color.Cyan.copy(alpha = 0.2f) else Color.Transparent,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(2.dp)
                    ) {
                        if (layerIndex > 0) {
                            Button(
                                onClick = { onMoveLayer(layerIndex, layerIndex - 1) },
                                contentPadding = PaddingValues(2.dp),
                                modifier = Modifier.size(20.dp)
                            ) {
                                Text("<", fontSize = 10.sp)
                            }
                        }

                        Button(
                            onClick = { onSelectLayer(layerIndex) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isLayerSelected) Color.Cyan else Color.Gray
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(currentLayers[layerIndex].name, color = if (isLayerSelected) Color.Black else Color.White, fontSize = 12.sp)
                        }

                        if (layerIndex < currentLayers.size - 1) {
                            Button(
                                onClick = { onMoveLayer(layerIndex, layerIndex + 1) },
                                contentPadding = PaddingValues(2.dp),
                                modifier = Modifier.size(20.dp)
                            ) {
                                Text(">", fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
            Button(onClick = onAddLayer, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                Text("+ Layer", fontSize = 12.sp)
            }
        }

        // 2. Drawing Canvas Area with clipping to prevent overbleed
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
                .background(Color.White)
        ) {
            DrawingCanvas(
                layers = currentLayers,
                currentLayerIndex = currentLayerIndex,
                toolState = toolState,
                onPathAddedToActiveLayer = onPathAdded
            )
        }

        // 3. Tool Palette Toolbar
        PaletteToolbar(
            toolState = toolState,
            onToolStateChanged = onToolStateChanged
        )

        // 4. Timeline / Frame Bar at the bottom with re-ordering controls
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .background(Color.Black.copy(alpha = 0.8f)),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(frames.size) { index ->
                FrameThumbnailItem(
                    index = index,
                    totalFrames = frames.size,
                    isSelected = index == currentFrameIndex,
                    onClick = { onSelectFrame(index) },
                    onMoveLeft = { onMoveFrame(index, index - 1) },
                    onMoveRight = { onMoveFrame(index, index + 1) }
                )
            }

            item {
                Box(
                    modifier = Modifier
                        .size(70.dp, 74.dp)
                        .background(Color.Gray, shape = RoundedCornerShape(8.dp))
                        .clickable { onAddFrame() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", color = Color.White, fontSize = 24.sp)
                }
            }
        }
    }
}

@Composable
fun FrameThumbnailItem(
    index: Int,
    totalFrames: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit
) {
    val borderColor = if (isSelected) Color.Cyan else Color.Transparent
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (index > 0) {
                Button(
                    onClick = onMoveLeft,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(18.dp, 24.dp)
                ) {
                    Text("<", fontSize = 10.sp)
                }
            }

            Box(
                modifier = Modifier
                    .size(54.dp, 54.dp)
                    .border(2.dp, borderColor, RoundedCornerShape(8.dp))
                    .background(Color.White, shape = RoundedCornerShape(8.dp))
                    .clickable { onClick() },
                contentAlignment = Alignment.Center
            ) {
                Text("${index + 1}", color = Color.Black)
            }

            if (index < totalFrames - 1) {
                Button(
                    onClick = onMoveRight,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(18.dp, 24.dp)
                ) {
                    Text(">", fontSize = 10.sp)
                }
            }
        }
    }
}