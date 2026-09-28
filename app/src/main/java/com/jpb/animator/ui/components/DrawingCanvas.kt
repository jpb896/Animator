package com.jpb.animator.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.jpb.animator.utils.DrawStyle
import com.jpb.animator.utils.DrawingToolState
import com.jpb.animator.utils.Layer
import com.jpb.animator.utils.StyledPath
import com.jpb.animator.utils.ToolType

@Composable
fun DrawingCanvas(
    layers: List<Layer>,
    currentLayerIndex: Int,
    toolState: DrawingToolState,
    onPathAddedToActiveLayer: (StyledPath) -> Unit
) {
    var currentPath by remember { mutableStateOf<Path?>(null) }
    var activeColor by remember { mutableStateOf(Color.Black) }
    var activeStrokeWidth by remember { mutableStateOf(8f) }
    var activeDrawStyle by remember { mutableStateOf(DrawStyle.STROKE) }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(toolState) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val path = Path().apply { moveTo(offset.x, offset.y) }
                        currentPath = path

                        activeStrokeWidth = toolState.strokeWidth
                        when (toolState.toolType) {
                            ToolType.PEN -> {
                                activeColor = toolState.currentColor
                                activeDrawStyle = DrawStyle.STROKE
                            }
                            ToolType.ERASER -> {
                                activeColor = Color.White
                                activeDrawStyle = DrawStyle.STROKE
                                activeStrokeWidth = toolState.strokeWidth * 2f
                            }
                            ToolType.FILL -> {
                                activeColor = toolState.currentColor
                                activeDrawStyle = DrawStyle.FILL
                            }
                        }
                    },
                    onDrag = { change, _ ->
                        currentPath?.let { path ->
                            path.lineTo(change.position.x, change.position.y)
                            currentPath = Path().apply { addPath(path) }
                        }
                    },
                    onDragEnd = {
                        currentPath?.let { path ->
                            val newStyledPath = StyledPath(
                                path = path,
                                color = activeColor,
                                strokeWidth = activeStrokeWidth,
                                drawStyle = activeDrawStyle
                            )
                            onPathAddedToActiveLayer(newStyledPath)
                            currentPath = null
                        }
                    },
                    onDragCancel = {
                        currentPath = null
                    }
                )
            }
    ) {
        // Draw background
        drawRect(color = Color.White)

        // Draw all layers in order (bottom to top) if they are visible
        layers.forEachIndexed { index, layer ->
            if (layer.isVisible) {
                layer.paths.forEach { styledPath ->
                    drawPath(
                        path = styledPath.path,
                        color = styledPath.color,
                        style = if (styledPath.drawStyle == DrawStyle.FILL) Fill else Stroke(
                            width = styledPath.strokeWidth,
                            cap = StrokeCap.Round
                        )
                    )
                }
            }
        }

        // Draw the active stroke preview in real time on the current layer
        currentPath?.let { path ->
            drawPath(
                path = path,
                color = activeColor,
                style = if (activeDrawStyle == DrawStyle.FILL) Fill else Stroke(
                    width = activeStrokeWidth,
                    cap = StrokeCap.Round
                )
            )
        }
    }
}