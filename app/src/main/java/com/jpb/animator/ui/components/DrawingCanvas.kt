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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import com.jpb.animator.utils.DrawStyle
import com.jpb.animator.utils.DrawingToolState
import com.jpb.animator.utils.FloodFillUtils
import com.jpb.animator.utils.Layer
import com.jpb.animator.utils.StyledPath
import com.jpb.animator.utils.ToolType
import androidx.core.graphics.get
import androidx.core.graphics.createBitmap

@Composable
fun DrawingCanvas(
    layers: List<Layer>,
    currentLayerIndex: Int,
    toolState: DrawingToolState,
    onPathAddedToActiveLayer: (StyledPath) -> Unit,
    onLayerBitmapUpdated: ((Bitmap) -> Unit)? = null
) {
    var currentPath by remember { mutableStateOf<Path?>(null) }
    var currentPoints by remember { mutableStateOf<MutableList<Offset>>(mutableListOf()) }

    var activeColor by remember { mutableStateOf(Color.Black) }
    var activeStrokeWidth by remember { mutableFloatStateOf(8f) }
    var activeDrawStyle by remember { mutableStateOf(DrawStyle.STROKE) }

    val currentLayer = layers.getOrNull(currentLayerIndex)

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(toolState, currentLayerIndex) {
                if (toolState.toolType == ToolType.FLOODFILL) {
                    detectTapGestures(
                        onTap = { offset ->
                            val bitmapWidth = size.width
                            val bitmapHeight = size.height
                            if (bitmapWidth <= 0 || bitmapHeight <= 0) return@detectTapGestures

                            // 1. Create a mutable bitmap representing the exact visual layout
                            val mutableBmp = createBitmap(bitmapWidth, bitmapHeight)
                            val androidCanvas = android.graphics.Canvas(mutableBmp)

                            // 2. Draw canvas background
                            androidCanvas.drawColor(android.graphics.Color.WHITE)

                            // 3. Draw existing layer bitmap content if present
                            currentLayer?.rasterBitmap?.let { existingBmp ->
                                androidCanvas.drawBitmap(existingBmp, 0f, 0f, null)
                            }

                            // 4. Draw all vector paths onto the bitmap so they act as fill boundaries
                            currentLayer?.paths?.forEach { styledPath ->
                                val paint = android.graphics.Paint().apply {
                                    isAntiAlias = true
                                    color = styledPath.color.toArgb()
                                    strokeWidth = styledPath.strokeWidth
                                    strokeCap = android.graphics.Paint.Cap.ROUND
                                    style = if (styledPath.drawStyle == DrawStyle.FILL) {
                                        android.graphics.Paint.Style.FILL
                                    } else {
                                        android.graphics.Paint.Style.STROKE
                                    }
                                }
                                androidCanvas.drawPath(styledPath.path.asAndroidPath(), paint)
                            }

                            val startX = offset.x.toInt().coerceIn(0, bitmapWidth - 1)
                            val startY = offset.y.toInt().coerceIn(0, bitmapHeight - 1)

                            val targetColor = mutableBmp[startX, startY]
                            val replacementColor = toolState.currentColor.toArgb()

                            // 5. Execute flood fill bounded by paths and canvas edges
                            val filledBitmap = FloodFillUtils.floodFill(
                                bitmap = mutableBmp,
                                startX = startX,
                                startY = startY,
                                targetColor = targetColor,
                                replacementColor = replacementColor
                            )

                            // 6. Update layer raster bitmap state
                            onLayerBitmapUpdated?.invoke(filledBitmap)
                        }
                    )
                } else {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val path = Path().apply { moveTo(offset.x, offset.y) }
                            currentPath = path
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
                                currentPoints.add(change.position)
                            }
                        },
                        onDragEnd = {
                            currentPath?.let { path ->
                                val newStyledPath = StyledPath(
                                    rawPath = path,
                                    rawPoints = currentPoints,
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