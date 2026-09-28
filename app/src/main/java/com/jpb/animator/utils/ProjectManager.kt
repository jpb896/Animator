package com.jpb.animator.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

object ProjectManager {

    fun saveProject(context: Context, projectId: String, frames: List<AnimationFrame>): Boolean {
        return try {
            val projectDir = File(context.filesDir, projectId)
            if (!projectDir.exists()) projectDir.mkdirs()

            frames.forEachIndexed { frameIndex, frame ->
                frame.layers.forEachIndexed { layerIndex, layer ->
                    layer.rasterBitmap?.let { bitmap ->
                        val bitmapFile = File(projectDir, "f${frameIndex}_l${layerIndex}.png")
                        FileOutputStream(bitmapFile).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                    }
                }
            }

            val file = File(projectDir, "animation_project.dat")
            ObjectOutputStream(FileOutputStream(file)).use { it.writeObject(frames) }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun loadProject(context: Context, projectId: String): List<AnimationFrame>? {
        return try {
            val projectDir = File(context.filesDir, projectId)
            val file = File(projectDir, "animation_project.dat")
            if (!file.exists()) return null

            val frames = ObjectInputStream(file.inputStream()).use { it.readObject() } as List<AnimationFrame>

            frames.forEachIndexed { frameIndex, frame ->
                frame.layers.forEachIndexed { layerIndex, layer ->
                    // Re-link saved bitmaps back into layers
                    val bitmapFile = File(projectDir, "f${frameIndex}_l${layerIndex}.png")
                    if (bitmapFile.exists()) {
                        layer.rasterBitmap = BitmapFactory.decodeFile(bitmapFile.absolutePath)
                    }
                }
            }
            frames
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}