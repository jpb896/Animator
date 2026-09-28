package com.jpb.animator.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput

@Composable
fun DrawingCanvas(
    paths: List<Path>,
    onPathAdded: (Path) -> Unit,
    onCurrentPathChanged: (Path?) -> Unit,
    currentPath: Path?
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val newPath = Path().apply {
                            moveTo(offset.x, offset.y)
                        }
                        onCurrentPathChanged(newPath)
                    },
                    onDrag = { change, _ ->
                        currentPath?.let { path ->
                            val position = change.position
                            path.lineTo(position.x, position.y)
                            // Force redraw by updating current path reference
                            onCurrentPathChanged(Path().apply { addPath(path) })
                        }
                    },
                    onDragEnd = {
                        currentPath?.let { path ->
                            onPathAdded(path)
                            onCurrentPathChanged(null)
                        }
                    },
                    onDragCancel = {
                        onCurrentPathChanged(null)
                    }
                )
            }
    ) {
        // 1. Draw all previously saved paths for this frame
        paths.forEach { path ->
            drawPath(
                path = path,
                color = Color.Black,
                style = Stroke(width = 8f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }

        // 2. Draw the path currently being dragged
        currentPath?.let { path ->
            drawPath(
                path = path,
                color = Color.Black,
                style = Stroke(width = 8f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }
    }
}