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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jpb.animator.ui.components.DrawingCanvas
import com.jpb.animator.ui.components.PaletteToolbar
import com.jpb.animator.ui.theme.AnimatorTheme
import com.jpb.animator.utils.AnimationFrame
import com.jpb.animator.utils.DrawingToolState
import com.jpb.animator.utils.Layer
import com.jpb.animator.utils.ProjectManager
import com.jpb.animator.utils.StyledPath
import com.jpb.animator.utils.duplicateCurrentFrame
import com.jpb.animator.utils.exportAnimationNative
import com.jpb.animator.utils.renderFrameToBitmap
import com.jpb.animator.utils.swap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class GlobalAppSettings(
    val isOnionSkinningEnabled: Boolean = false,
    val onionSkinOpacity: Float = 0.3f
)

sealed class AppScreen {
    object Home : AppScreen()
    data class Editor(val projectId: String, val isNew: Boolean) : AppScreen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AnimatorTheme {
                var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Home) }

                when (val screen = currentScreen) {
                    is AppScreen.Home -> {
                        HomeScreen(
                            onCreateNewProject = {
                                val newProjectId = "project_${System.currentTimeMillis()}"
                                currentScreen = AppScreen.Editor(projectId = newProjectId, isNew = true)
                            },
                            onOpenProject = { projectId ->
                                currentScreen = AppScreen.Editor(projectId = projectId, isNew = false)
                            }
                        )
                    }
                    is AppScreen.Editor -> {
                        EditorFlow(
                            projectId = screen.projectId,
                            isNewProject = screen.isNew,
                            onBackToHome = {
                                currentScreen = AppScreen.Home
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    onCreateNewProject: () -> Unit,
    onOpenProject: (String) -> Unit
) {
    val context = LocalContext.current
    var projectFolders by remember { mutableStateOf<List<File>>(emptyList()) }
    var projectTitlesState by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    LaunchedEffect(Unit) {
        val rootDir = context.filesDir
        val projects = rootDir.listFiles()?.filter { it.isDirectory && it.name.startsWith("project_") }?.sortedByDescending { it.name }?.toList() ?: emptyList()
        projectFolders = projects

        val titlesMap = mutableMapOf<String, String>()
        for (folder in projects) {
            titlesMap[folder.name] = ProjectManager.loadProjectTitle(context, folder.name)
        }
        projectTitlesState = titlesMap
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.DarkGray)
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("My Animations", color = Color.White, fontSize = 24.sp)
            Button(onClick = onCreateNewProject) {
                Text("+ New Project")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Box(
                    modifier = Modifier
                        .height(180.dp)
                        .fillMaxWidth()
                        .background(Color.Gray.copy(alpha = 0.3f), shape = RoundedCornerShape(12.dp))
                        .border(2.dp, Color.Gray, RoundedCornerShape(12.dp))
                        .clickable { onCreateNewProject() },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("+", color = Color.White, fontSize = 36.sp)
                        Text("Create Project", color = Color.White, fontSize = 14.sp)
                    }
                }
            }

            items(projectFolders) { projectFolder ->
                val displayTitle = projectTitlesState[projectFolder.name] ?: projectFolder.name.removePrefix("project_")
                Box(
                    modifier = Modifier
                        .height(180.dp)
                        .fillMaxWidth()
                        .background(Color.White, shape = RoundedCornerShape(12.dp))
                        .clickable { onOpenProject(projectFolder.name) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(displayTitle, color = Color.Black, fontSize = 16.sp, maxLines = 1)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Tap to edit", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun EditorFlow(
    projectId: String,
    isNewProject: Boolean,
    onBackToHome: () -> Unit
) {
    val context = LocalContext.current
    var frames by remember { mutableStateOf(listOf(AnimationFrame(layers = listOf(Layer(name = "Layer 1"))))) }
    var projectTitle by remember { mutableStateOf(projectId.removePrefix("project_")) }
    var fps by remember { mutableIntStateOf(12) }
    var canvasWidth by remember { mutableIntStateOf(400) }
    var canvasHeight by remember { mutableIntStateOf(400) }
    var backgroundColorInt by remember { mutableIntStateOf(Color.White.toArgb()) }
    var globalSettings by remember { mutableStateOf(GlobalAppSettings()) }

    var currentFrameIndex by remember { mutableIntStateOf(0) }
    var currentLayerIndex by remember { mutableIntStateOf(0) }
    var toolState by remember { mutableStateOf(DrawingToolState()) }
    var showSettingsDialog by remember { mutableStateOf(isNewProject) }

    LaunchedEffect(projectId) {
        val loadedFrames = ProjectManager.loadProject(context, projectId)
        if (!loadedFrames.isNullOrEmpty()) {
            frames = loadedFrames
        }

        val loadedMeta = ProjectManager.loadProjectMetadata(context, projectId)
        if (loadedMeta != null) {
            projectTitle = loadedMeta["title"] as? String ?: projectTitle
            fps = loadedMeta["fps"] as? Int ?: fps
            canvasWidth = loadedMeta["width"] as? Int ?: canvasWidth
            canvasHeight = loadedMeta["height"] as? Int ?: canvasHeight
            backgroundColorInt = loadedMeta["bgColor"] as? Int ?: backgroundColorInt
        }
    }

    EditorScreen(
        projectId = projectId,
        projectTitle = projectTitle,
        fps = fps,
        width = canvasWidth,
        height = canvasHeight,
        backgroundColorInt = backgroundColorInt,
        frames = frames,
        currentFrameIndex = currentFrameIndex,
        currentLayerIndex = currentLayerIndex,
        toolState = toolState,
        globalSettings = globalSettings,
        showSettingsDialog = showSettingsDialog,
        onToggleSettingsDialog = { showSettingsDialog = it },
        onToolStateChanged = { newToolState -> toolState = newToolState },
        onUpdateProjectSettings = { newTitle, newFps, newWidth, newHeight, newColorInt, newGlobalSettings ->
            projectTitle = newTitle
            fps = newFps
            canvasWidth = newWidth
            canvasHeight = newHeight
            backgroundColorInt = newColorInt
            globalSettings = newGlobalSettings
        },
        onBack = {
            ProjectManager.saveProject(context, projectId, frames)
            ProjectManager.saveProjectMetadata(context, projectId, projectTitle, fps, canvasWidth, canvasHeight, backgroundColorInt)
            onBackToHome()
        },
        onSaveProject = {
            val successFrames = ProjectManager.saveProject(context, projectId, frames)
            val successMeta = ProjectManager.saveProjectMetadata(context, projectId, projectTitle, fps, canvasWidth, canvasHeight, backgroundColorInt)
            Toast.makeText(context, if (successFrames && successMeta) "Project saved!" else "Save failed", Toast.LENGTH_SHORT).show()
        },
        onPathAdded = { newStyledPath ->
            val frame = frames.getOrNull(currentFrameIndex) ?: return@EditorScreen
            val updatedLayers = frame.layers.toMutableList()
            val targetLayer = updatedLayers.getOrElse(currentLayerIndex) { updatedLayers.first() }

            val newLayerPaths = targetLayer.paths + newStyledPath
            updatedLayers[currentLayerIndex] = targetLayer.copy(paths = newLayerPaths)

            val mutableFrames = frames.toMutableList()
            mutableFrames[currentFrameIndex] = frame.copy(layers = updatedLayers)
            frames = mutableFrames
        },
        onLayerBitmapUpdated = { newBitmap ->
            val frame = frames.getOrNull(currentFrameIndex) ?: return@EditorScreen
            val updatedLayers = frame.layers.toMutableList()
            val targetLayer = updatedLayers.getOrElse(currentLayerIndex) { updatedLayers.first() }

            updatedLayers[currentLayerIndex] = targetLayer.copy(rasterBitmap = newBitmap)

            val mutableFrames = frames.toMutableList()
            mutableFrames[currentFrameIndex] = frame.copy(layers = updatedLayers)
            frames = mutableFrames
        },
        onAddFrame = {
            val mutableFrames = frames.toMutableList()
            mutableFrames.add(AnimationFrame(layers = listOf(Layer(name = "Layer 1"))))
            frames = mutableFrames
            currentFrameIndex = frames.size - 1
            currentLayerIndex = 0
        },
        onDuplicateFrame = {
            val updatedFrames = duplicateCurrentFrame(frames, currentFrameIndex)
            if (updatedFrames != frames) {
                frames = updatedFrames
                currentFrameIndex += 1
                currentLayerIndex = 0
            }
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
            val frame = frames.getOrNull(currentFrameIndex) ?: return@EditorScreen
            val newLayerName = "Layer ${frame.layers.size + 1}"
            val updatedLayers = frame.layers + Layer(name = newLayerName)

            val mutableFrames = frames.toMutableList()
            mutableFrames[currentFrameIndex] = frame.copy(layers = updatedLayers)
            frames = mutableFrames
            currentLayerIndex = updatedLayers.size - 1
        },
        onSelectLayer = { index ->
            currentLayerIndex = index
        },
        onMoveLayer = { fromIndex, toIndex ->
            val frame = frames.getOrNull(currentFrameIndex) ?: return@EditorScreen
            if (toIndex in frame.layers.indices) {
                val updatedLayers = frame.layers.swap(fromIndex, toIndex)
                val mutableFrames = frames.toMutableList()
                mutableFrames[currentFrameIndex] = frame.copy(layers = updatedLayers)
                frames = mutableFrames
                currentLayerIndex = toIndex
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    projectId: String,
    projectTitle: String,
    fps: Int,
    width: Int,
    height: Int,
    backgroundColorInt: Int,
    frames: List<AnimationFrame>,
    currentFrameIndex: Int,
    currentLayerIndex: Int,
    toolState: DrawingToolState,
    globalSettings: GlobalAppSettings,
    showSettingsDialog: Boolean,
    onToggleSettingsDialog: (Boolean) -> Unit,
    onToolStateChanged: (DrawingToolState) -> Unit,
    onUpdateProjectSettings: (String, Int, Int, Int, Int, GlobalAppSettings) -> Unit,
    onBack: () -> Unit,
    onSaveProject: () -> Unit,
    onPathAdded: (StyledPath) -> Unit,
    onLayerBitmapUpdated: (Bitmap) -> Unit,
    onAddFrame: () -> Unit,
    onDuplicateFrame: () -> Unit,
    onSelectFrame: (Int) -> Unit,
    onMoveFrame: (Int, Int) -> Unit,
    onAddLayer: () -> Unit,
    onSelectLayer: (Int) -> Unit,
    onMoveLayer: (Int, Int) -> Unit
) {
    val context = LocalContext.current

    if (showSettingsDialog) {
        EmbeddedProjectSettingsDialog(
            projectTitle = projectTitle,
            fps = fps,
            width = width,
            height = height,
            backgroundColorInt = backgroundColorInt,
            globalSettings = globalSettings,
            onDismiss = { onToggleSettingsDialog(false) },
            onSave = { updatedTitle, updatedFps, updatedWidth, updatedHeight, updatedColorInt, updatedGlobalSettings ->
                onUpdateProjectSettings(updatedTitle, updatedFps, updatedWidth, updatedHeight, updatedColorInt, updatedGlobalSettings)

                ProjectManager.saveProjectMetadata(
                    context = context,
                    projectId = projectId,
                    title = updatedTitle,
                    fps = updatedFps,
                    width = updatedWidth,
                    height = updatedHeight,
                    bgColor = updatedColorInt
                )

                onToggleSettingsDialog(false)
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = projectTitle, maxLines = 1, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { onToggleSettingsDialog(true) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.DarkGray)
            )
        },
        containerColor = Color.DarkGray
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val coroutineScope = rememberCoroutineScope()
                var pendingFrameIndex by remember { mutableStateOf<Int?>(null) }

                val createVideoLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("video/mp4")
                ) { uri: Uri? ->
                    if (uri != null) {
                        Toast.makeText(context, "Exporting video...", Toast.LENGTH_SHORT).show()
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val exportResult = exportAnimationNative(
                                    context = context,
                                    frames = frames,
                                    fps = fps,
                                    width = width,
                                    height = height,
                                    backgroundColorInt = backgroundColorInt
                                )

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

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(onClick = onSaveProject, contentPadding = PaddingValues(horizontal = 6.dp)) {
                        Text("Save", fontSize = 12.sp)
                    }

                    Button(onClick = {
                        pendingFrameIndex = currentFrameIndex
                        saveSingleFrameLauncher.launch("Frame_${currentFrameIndex + 1}.png")
                    }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                        Text("Frame", fontSize = 12.sp)
                    }

                    Button(onClick = {
                        Toast.makeText(context, "Rendering video...", Toast.LENGTH_SHORT).show()
                        coroutineScope.launch {
                            createVideoLauncher.launch("$projectTitle.mp4")
                        }
                    }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                        Text("Export", fontSize = 12.sp)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(
                            width = width.dp,
                            height = height.dp
                        )
                        .background(Color(backgroundColorInt))
                        .clipToBounds()
                ) {
                    val activeLayers = frames.getOrNull(currentFrameIndex)?.layers ?: emptyList()
                    val previousLayers = if (currentFrameIndex > 0) frames.getOrNull(currentFrameIndex - 1)?.layers else null

                    DrawingCanvas(
                        layers = activeLayers,
                        previousLayers = previousLayers,
                        currentLayerIndex = currentLayerIndex,
                        toolState = toolState,
                        globalSettings = globalSettings,
                        onPathAddedToActiveLayer = onPathAdded,
                        onLayerBitmapUpdated = onLayerBitmapUpdated
                    )
                }
            }

            PaletteToolbar(
                toolState = toolState,
                onToolStateChanged = onToolStateChanged
            )

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
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .size(70.dp, 74.dp)
                                .background(Color.Gray, shape = RoundedCornerShape(8.dp))
                                .clickable { onAddFrame() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+", color = Color.White, fontSize = 24.sp)
                        }

                        Box(
                            modifier = Modifier
                                .size(70.dp, 74.dp)
                                .background(Color.DarkGray, shape = RoundedCornerShape(8.dp))
                                .clickable { onDuplicateFrame() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate Frame", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmbeddedProjectSettingsDialog(
    projectTitle: String,
    fps: Int,
    width: Int,
    height: Int,
    backgroundColorInt: Int,
    globalSettings: GlobalAppSettings,
    onDismiss: () -> Unit,
    onSave: (String, Int, Int, Int, Int, GlobalAppSettings) -> Unit
) {
    var title by remember { mutableStateOf(projectTitle) }
    var fpsText by remember { mutableStateOf(fps.toString()) }
    var widthText by remember { mutableStateOf(width.toString()) }
    var heightText by remember { mutableStateOf(height.toString()) }
    var onionSkinningEnabled by remember { mutableStateOf(globalSettings.isOnionSkinningEnabled) }

    val colorOptions = listOf(Color.White, Color.LightGray, Color.DarkGray, Color.Black, Color.Yellow)
    var selectedColor by remember { mutableStateOf(Color(backgroundColorInt)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Project Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Project Title") }
                )
                OutlinedTextField(
                    value = fpsText,
                    onValueChange = { fpsText = it },
                    label = { Text("FPS") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = widthText,
                    onValueChange = { widthText = it },
                    label = { Text("Width (dp)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = heightText,
                    onValueChange = { heightText = it },
                    label = { Text("Height (dp)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Onion-Skinning", fontSize = 14.sp, color = Color.White)
                    Switch(
                        checked = onionSkinningEnabled,
                        onCheckedChange = { onionSkinningEnabled = it }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text("Background Color", fontSize = 14.sp, color = Color.White)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    colorOptions.forEach { color ->
                        val isSelected = color == selectedColor
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) Color.Cyan else Color.Gray,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = color }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val newFps = fpsText.toIntOrNull() ?: fps
                val newWidth = widthText.toIntOrNull() ?: width
                val newHeight = heightText.toIntOrNull() ?: height
                val newColorInt = selectedColor.toArgb()
                val newGlobalSettings = globalSettings.copy(isOnionSkinningEnabled = onionSkinningEnabled)

                onSave(title, newFps, newWidth, newHeight, newColorInt, newGlobalSettings)
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
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