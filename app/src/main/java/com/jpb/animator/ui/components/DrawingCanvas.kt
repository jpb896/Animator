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
import com.jpb.animator.GlobalAppSettings
import com.jpb.animator.utils.DrawStyle
import com.jpb.animator.utils.DrawingToolState
import com.jpb.animator.utils.FloodFillUtils
import com.jpb.animator.utils.Layer
import com.jpb.animator.utils.StyledPath
import com.jpb.animator.utils.ToolType

@Composable
fun DrawingCanvas(
    layers: List<Layer>,
    previousLayers: List<Layer>?, // Added to support onion skinning from the previous frame
    currentLayerIndex: Int,
    toolState: DrawingToolState,
    globalSettings: GlobalAppSettings, // Added to check if onion skinning is active
    onPathAddedToActiveLayer: (StyledPath) -> Unit,
    onLayerBitmapUpdated: ((Bitmap) -> Unit)? = null
) {
    var currentPath by remember { mutableStateOf<Path?>(null) }
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
                    detectTapGestures(
                        onTap = { offset ->
                            val bitmapWidth = size.width.toInt()
                            val bitmapHeight = size.height.toInt()
                            if (bitmapWidth <= 0 || bitmapHeight <= 0) return@detectTapGestures

                            val mutableBmp = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
                            val androidCanvas = android.graphics.Canvas(mutableBmp)

                            androidCanvas.drawColor(android.graphics.Color.WHITE)

                            currentLayer?.rasterBitmap?.let { existingBmp ->
                                androidCanvas.drawBitmap(existingBmp, 0f, 0f, null)
                            }

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

                            val targetColor = mutableBmp.getPixel(startX, startY)
                            val replacementColor = toolState.currentColor.toArgb()

                            val filledBitmap = FloodFillUtils.floodFill(
                                bitmap = mutableBmp,
                                startX = startX,
                                startY = startY,
                                targetColor = targetColor,
                                replacementColor = replacementColor
                            )

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

        // 1. Render Onion-Skinning Ghost of Previous Frame if enabled
        if (globalSettings.isOnionSkinningEnabled && previousLayers != null) {
            val alpha = globalSettings.onionSkinOpacity
            previousLayers.forEach { layer ->
                if (layer.isVisible) {
                    layer.rasterBitmap?.let { bmp ->
                        drawImage(image = bmp.asImageBitmap(), alpha = alpha)
                    }
                    layer.paths.forEach { styledPath ->
                        drawPath(
                            path = styledPath.path,
                            color = styledPath.color.copy(alpha = styledPath.color.alpha * alpha),
                            style = if (styledPath.drawStyle == DrawStyle.FILL) Fill else Stroke(
                                width = styledPath.strokeWidth,
                                cap = StrokeCap.Round
                            )
                        )
                    }
                }
            }
        }

        // 2. Draw current frame layers normally on top
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

        // Draw active stroke preview
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