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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jpb.animator.ui.components.DrawingCanvas
import com.jpb.animator.ui.theme.AnimatorTheme
import com.jpb.animator.utils.exportAnimationNative
import com.jpb.animator.utils.renderFrameToBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// Represents a single frame containing its drawn paths
data class AnimationFrame(
    val paths: List<Path> = emptyList()
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AnimatorTheme {
                // State for frames and current selection
                var frames by remember { mutableStateOf(listOf(AnimationFrame())) }
                var currentFrameIndex by remember { mutableStateOf(0) }

                // Track the path currently being drawn with a finger/stylus
                var currentPath by remember { mutableStateOf<Path?>(null) }

                EditorScreen(
                    currentFrameIndex = currentFrameIndex,
                    frames = frames,
                    onPathAdded = { newPath ->
                        // Add path to the current frame
                        val updatedPaths = frames[currentFrameIndex].paths + newPath
                        frames = frames.toMutableList().apply {
                            this[currentFrameIndex] = this[currentFrameIndex].copy(paths = updatedPaths)
                        }
                    },
                    onAddFrame = {
                        frames = frames + AnimationFrame(paths = emptyList())
                        currentFrameIndex = frames.size - 1
                    },
                    onSelectFrame = { index ->
                        currentFrameIndex = index
                    }
                )
            }
        }
    }
}

@Composable
fun EditorScreen(
    currentFrameIndex: Int,
    frames: List<AnimationFrame>,
    onPathAdded: (Path) -> Unit,
    onAddFrame: () -> Unit,
    onSelectFrame: (Int) -> Unit
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
                .padding(4.dp),
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
                            // 1. Run the native video export helper
                            val exportResult = exportAnimationNative(context, frames)

                            if (exportResult.first) {
                                val generatedFile = File(exportResult.second)

                                // 2. Copy the generated MP4 file bytes into the user-selected SAF Uri stream
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
                                    val bitmap = renderFrameToBitmap(frameToSave) // Your rendering helper
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
            Column() {
                Button(onClick = {
                    pendingFrameIndex = currentFrameIndex
                    saveSingleFrameLauncher.launch("Frame_${currentFrameIndex + 1}.png")
                }) {
                    Text("Save current frame")
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

        // 2. Drawing Canvas Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.White)
        ) {
            val currentPaths = frames.getOrNull(currentFrameIndex)?.paths ?: emptyList()

            DrawingCanvas(
                paths = currentPaths,
                onPathAdded = onPathAdded
            )
        }

        // 3. Timeline / Frame Bar at the bottom
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(Color.Black.copy(alpha = 0.8f)),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(frames.size) { index ->
                FrameThumbnailItem(
                    index = index,
                    isSelected = index == currentFrameIndex,
                    onClick = { onSelectFrame(index) }
                )
            }

            // Button to add a new frame
            item {
                Box(
                    modifier = Modifier
                        .size(70.dp, 84.dp)
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
fun FrameThumbnailItem(index: Int, isSelected: Boolean, onClick: () -> Unit) {
    val borderColor = if (isSelected) Color.Cyan else Color.Transparent
    Box(
        modifier = Modifier
            .size(70.dp, 84.dp)
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .background(Color.White, shape = RoundedCornerShape(8.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text("${index + 1}", color = Color.Black)
    }
}