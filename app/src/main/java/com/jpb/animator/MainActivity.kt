package com.jpb.animator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jpb.animator.ui.theme.AnimatorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AnimatorTheme {
                // Manage state for frames and current selection
                var currentFrameIndex by remember { mutableStateOf(0) }
                // For now, let's track a list of frame counts or data structures
                var totalFrames by remember { mutableStateOf(1) }

                EditorScreen(
                    currentFrameIndex = currentFrameIndex,
                    totalFrames = totalFrames,
                    onAddFrame = {
                        totalFrames++
                        currentFrameIndex = totalFrames - 1 // Switch to the new frame
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
    totalFrames: Int,
    onAddFrame: () -> Unit,
    onSelectFrame: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.DarkGray)
            .statusBarsPadding() // Ensures content doesn't hide behind system status bar
    ) {
        // 1. Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("My Animation", color = Color.White, fontSize = 20.sp)
            Button(onClick = { /* Handle Export */ }) {
                Text("Export")
            }
        }

        // 2. Drawing Canvas Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.White)
        ) {
            Text(
                text = "Frame ${currentFrameIndex + 1}",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                color = Color.Gray
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
            items(totalFrames) { index ->
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