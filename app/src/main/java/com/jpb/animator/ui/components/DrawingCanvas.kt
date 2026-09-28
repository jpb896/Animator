package com.jpb.animator.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput

@Composable
fun DrawingCanvas(
    paths: List<Path>,
    onPathAdded: (Path) -> Unit
) {
    // Keep track of the path currently being drawn right inside the canvas
    var currentPath by remember { mutableStateOf<Path?>(null) }
    var currentMotionEvent by remember { mutableStateOf<Offset?>(null) }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val path = Path().apply {
                            moveTo(offset.x, offset.y)
                        }
                        currentPath = path
                    },
                    onDrag = { change, _ ->
                        currentPath?.let { path ->
                            val position = change.position
                            path.lineTo(position.x, position.y)
                            // Force a recomposition by re-assigning the path
                            currentPath = Path().apply { addPath(path) }
                        }
                    },
                    onDragEnd = {
                        currentPath?.let { path ->
                            onPathAdded(path)
                            currentPath = null
                        }
                    },
                    onDragCancel = {
                        currentPath = null
                    }
                )
            }
    ) {
        // Draw all completed paths
        paths.forEach { path ->
            drawPath(
                path = path,
                color = Color.Black,
                style = Stroke(width = 8f, cap = StrokeCap.Round)
            )
        }

        // Draw the active stroke
        currentPath?.let { path ->
            drawPath(
                path = path,
                color = Color.Black,
                style = Stroke(width = 8f, cap = StrokeCap.Round)
            )
        }
    }
}