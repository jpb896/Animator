package com.jpb.animator.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.asAndroidPath
import com.jpb.animator.AnimationFrame
import java.io.File
import java.io.FileOutputStream
import androidx.core.graphics.createBitmap

fun saveIndividualFrame(context: Context, frame: AnimationFrame, frameIndex: Int, width: Int = 1080, height: Int = 1080): File {
    val framesDir = File(context.filesDir, "project_frames").apply { mkdirs() }
    val frameFile = File(framesDir, "frame_${frameIndex + 1}.png")

    val bitmap = createBitmap(width, height)
    val androidCanvas = AndroidCanvas(bitmap)
    androidCanvas.drawColor(AndroidColor.WHITE)

    val paint = Paint().apply {
        color = AndroidColor.BLACK
        strokeWidth = 8f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    frame.paths.forEach { composePath ->
        androidCanvas.drawPath(composePath.asAndroidPath(), paint)
    }

    FileOutputStream(frameFile).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }

    return frameFile
}