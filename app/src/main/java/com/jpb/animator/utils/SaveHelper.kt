package com.jpb.animator.utils

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream

fun saveFrameToFile(context: Context, frameIndex: Int, bitmap: Bitmap): File {
    val directory = File(context.filesDir, "animations").apply { mkdirs() }
    val file = File(directory, "frame_$frameIndex.png")

    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    return file
}