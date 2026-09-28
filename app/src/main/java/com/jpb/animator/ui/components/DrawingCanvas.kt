package com.jpb.animator.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.jpb.animator.utils.DrawStyle
import com.jpb.animator.utils.DrawingToolState
import com.jpb.animator.utils.FloodFillUtils
import com.jpb.animator.utils.Layer
import com.jpb.animator.utils.StyledPath
import com.jpb.animator.utils.ToolType

@Composable
fun DrawingCanvas(
    layers: List<Layer>,
    currentLayerIndex: Int,
    toolState: DrawingToolState,
    onPathAddedToActiveLayer: (StyledPath) -> Unit,
    onLayerBitmapUpdated: ((Bitmap) -> Unit)? = null
) {
    var currentPath by remember { mutableStateOf<Path?>(null) }
    // 1. Keep track of points for the current stroke
    var currentPoints by remember { mutableStateOf<MutableList<Offset>>(mutableListOf()) }

    var activeColor by remember { mutableStateOf(Color.Black) }
    var activeStrokeWidth by remember { mutableStateOf(8f) }
    var activeDrawStyle by remember { mutableStateOf(DrawStyle.STROKE) }

    val currentLayer = layers.getOrNull(currentLayerIndex)

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(toolState, currentLayerIndex) {
                if (toolState.toolType == ToolType.FLOODFILL) {
                    // ... (keep your flood fill tap gestures as they are) ...
                } else {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val path = Path().apply { moveTo(offset.x, offset.y) }
                            currentPath = path

                            // 2. Clear/Initialize points list for the new stroke
                            currentPoints = mutableListOf(offset)

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
                                else -> {}
                            }
                        },
                        onDrag = { change, _ ->
                            currentPath?.let { path ->
                                path.lineTo(change.position.x, change.position.y)
                                currentPath = Path().apply { addPath(path) }

                                // 3. Record every dragged point
                                currentPoints.add(change.position)
                            }
                        },
                        onDragEnd = {
                            currentPath?.let { path ->
                                val newStyledPath = StyledPath(
                                    rawPath = path,
                                    rawPoints = currentPoints, // 4. Pass the collected points here!
                                    color = activeColor,
                                    strokeWidth = activeStrokeWidth,
                                    drawStyle = activeDrawStyle
                                )
                                onPathAddedToActiveLayer(newStyledPath)
                                currentPath = null
                                currentPoints = mutableListOf()
                            }
                        },
                        onDragCancel = {
                            currentPath = null
                            currentPoints = mutableListOf()
                        }
                    )
                }
            }
    ) {
        // Draw background
        drawRect(color = Color.White)

        // Draw all layers in order (bottom to top) if they are visible
        layers.forEach { layer ->
            if (layer.isVisible) {
                layer.rasterBitmap?.let { bmp ->
                    drawImage(bmp.asImageBitmap())
                }
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