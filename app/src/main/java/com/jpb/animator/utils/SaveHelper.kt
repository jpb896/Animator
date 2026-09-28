package com.jpb.animator.utils

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.core.graphics.createBitmap

fun renderFrameToBitmap(frame: AnimationFrame, width: Int = 1080, height: Int = 1080): Bitmap {
    val bitmap = createBitmap(width, height)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)

    frame.paths.forEach { styledPath ->
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

    return bitmap
}