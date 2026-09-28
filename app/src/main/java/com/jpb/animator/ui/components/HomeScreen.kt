package com.jpb.animator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

@Composable
fun HomeScreen(
    onCreateNewProject: () -> Unit,
    onOpenProject: (String) -> Unit
) {
    val context = LocalContext.current
    var projectFolders by remember { mutableStateOf<List<File>>(emptyList()) }

    // Load available project folders from internal storage on launch
    LaunchedEffect(Unit) {
        val rootDir = context.filesDir
        val projects = rootDir.listFiles()?.filter { it.isDirectory && it.name.startsWith("project_") }?.toList() ?: emptyList()
        projectFolders = projects
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.DarkGray)
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        // App Title & Header
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

        // Project Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Create New Project Card
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

            // Existing Project Cards
            items(projectFolders) { projectFolder ->
                val projectName = projectFolder.name.removePrefix("project_")
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
                        Text(projectName, color = Color.Black, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Tap to edit", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}