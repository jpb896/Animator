package com.jpb.animator.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    var activeColor by remember { mutableStateOf(Color.Black) }
    var activeStrokeWidth by remember { mutableStateOf(8f) }
    var activeDrawStyle by remember { mutableStateOf(DrawStyle.STROKE) }

    val currentLayer = layers.getOrNull(currentLayerIndex)

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(toolState, currentLayerIndex) {
                if (toolState.toolType == ToolType.FLOODFILL) {
                    detectTapGestures(
                        onTap = { offset ->
                            if (currentLayer == null) return@detectTapGestures

                            val width = size.width.toInt().coerceAtLeast(1)
                            val height = size.height.toInt().coerceAtLeast(1)

                            // Use existing layer bitmap or create a base bitmap from vector paths
                            val androidBitmap = currentLayer.rasterBitmap?.copy(Bitmap.Config.ARGB_8888, true)
                                ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                                    val canvas = android.graphics.Canvas(this)
                                    canvas.drawColor(android.graphics.Color.WHITE)
                                    currentLayer.paths.forEach { styledPath ->
                                        val paint = android.graphics.Paint().apply {
                                            strokeWidth = styledPath.strokeWidth
                                            style = if (styledPath.drawStyle == DrawStyle.FILL) {
                                                android.graphics.Paint.Style.FILL
                                            } else {
                                                android.graphics.Paint.Style.STROKE
                                            }
                                            strokeCap = android.graphics.Paint.Cap.ROUND
                                            isAntiAlias = true
                                            color = android.graphics.Color.argb(
                                                styledPath.color.alpha,
                                                styledPath.color.red,
                                                styledPath.color.green,
                                                styledPath.color.blue
                                            )
                                        }
                                        canvas.drawPath(styledPath.path.asAndroidPath(), paint)
                                    }
                                }

                            val startX = offset.x.toInt().coerceIn(0, width - 1)
                            val startY = offset.y.toInt().coerceIn(0, height - 1)
                            val targetColor = androidBitmap.getPixel(startX, startY)

                            val replacementColor = android.graphics.Color.argb(
                                toolState.currentColor.alpha,
                                toolState.currentColor.red,
                                toolState.currentColor.green,
                                toolState.currentColor.blue
                            )

                            // Perform bounded pixel flood fill
                            val filledBitmap = FloodFillUtils.floodFill(
                                androidBitmap,
                                startX,
                                startY,
                                targetColor,
                                replacementColor
                            )

                            onLayerBitmapUpdated?.invoke(filledBitmap)
                        }
                    )
                } else {
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
                                else -> {}
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