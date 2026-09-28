package com.jpb.animator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jpb.animator.utils.DrawingToolState
import com.jpb.animator.utils.ToolType

@Composable
fun PaletteToolbar(
    toolState: DrawingToolState,
    onToolStateChanged: (DrawingToolState) -> Unit
) {
    val colors = listOf(Color.Black, Color.Red, Color.Blue, Color.Green, Color.Yellow, Color.Magenta)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp)
    ) {
        // Tool Selector (Pen, Eraser, Fill)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = { onToolStateChanged(toolState.copy(toolType = ToolType.PEN)) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (toolState.toolType == ToolType.PEN) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
            ) { Text("Pen") }

            Button(
                onClick = { onToolStateChanged(toolState.copy(toolType = ToolType.ERASER)) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (toolState.toolType == ToolType.ERASER) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
            ) { Text("Eraser") }

            Button(
                onClick = { onToolStateChanged(toolState.copy(toolType = ToolType.FILL)) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (toolState.toolType == ToolType.FILL) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
            ) { Text("Fill Shape") }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Color Palette (Active for Pen and Fill)
        if (toolState.toolType != ToolType.ERASER) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                colors.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .padding(4.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(color)
                            .clickable {
                                onToolStateChanged(toolState.copy(currentColor = color))
                            }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Stroke Width Slider (Mainly for Pen and Eraser)
        if (toolState.toolType != ToolType.FILL) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Size: ${toolState.strokeWidth.toInt()}", modifier = Modifier.padding(end = 8.dp))
                Slider(
                    value = toolState.strokeWidth,
                    onValueChange = { newWidth ->
                        onToolStateChanged(toolState.copy(strokeWidth = newWidth))
                    },
                    valueRange = 2f..48f,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}