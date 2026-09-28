package com.jpb.animator.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import androidx.compose.ui.graphics.asAndroidPath
import androidx.core.graphics.createBitmap

fun renderFrameToBitmap(frame: AnimationFrame, width: Int = 1080, height: Int = 1080): Bitmap {
    val bitmap = createBitmap(width, height)
    val canvas = Canvas(bitmap)
    canvas.drawColor(AndroidColor.WHITE)

    // Loop through layers and their styled paths
    frame.layers.forEach { layer ->
        if (layer.isVisible) {
            layer.paths.forEach { styledPath ->
                val paint = Paint().apply {
                    strokeWidth = styledPath.strokeWidth
                    style = if (styledPath.drawStyle == DrawStyle.FILL) {
                        Paint.Style.FILL
                    } else {
                        Paint.Style.STROKE
                    }
                    strokeCap = Paint.Cap.ROUND
                    isAntiAlias = true
                    color = AndroidColor.argb(
                        styledPath.color.alpha,
                        styledPath.color.red,
                        styledPath.color.green,
                        styledPath.color.blue
                    )
                }
                canvas.drawPath(styledPath.path.asAndroidPath(), paint)
            }
        }
    }

    return bitmap
}