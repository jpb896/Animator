package com.jpb.animator.utils

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.asAndroidPath
import com.jpb.animator.AnimationFrame
import androidx.core.graphics.createBitmap

fun renderFrameToBitmap(frame: AnimationFrame, width: Int = 1080, height: Int = 1080): Bitmap {
    val bitmap = createBitmap(width, height)
    val androidCanvas = AndroidCanvas(bitmap)
    androidCanvas.drawColor(AndroidColor.WHITE)

    val paint = android.graphics.Paint().apply {
        color = AndroidColor.BLACK
        strokeWidth = 8f
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
        isAntiAlias = true
    }

    frame.paths.forEach { composePath ->
        androidCanvas.drawPath(composePath.asAndroidPath(), paint)
    }
    return bitmap
}