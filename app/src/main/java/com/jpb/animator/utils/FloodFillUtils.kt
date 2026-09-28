package com.jpb.animator.utils

import android.graphics.Bitmap
import android.graphics.Color
import java.util.LinkedList
import java.util.Queue

object FloodFillUtils {

    fun floodFill(bitmap: Bitmap, startX: Int, startY: Int, targetColor: Int, replacementColor: Int): Bitmap {
        if (targetColor == replacementColor) return bitmap
        if (startX < 0 || startX >= bitmap.width || startY < 0 || startY >= bitmap.height) return bitmap

        val width = bitmap.width
        val height = bitmap.height

        // Grab all pixels into an array for high-performance manipulation
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        if (pixels[startY * width + startX] != targetColor) return bitmap

        val queue: Queue<Pair<Int, Int>> = LinkedList()
        queue.add(Pair(startX, startY))

        while (queue.isNotEmpty()) {
            val (x, y) = queue.poll()!!
            val index = y * width + x

            if (x !in 0..<width || y < 0 || y >= height) continue
            if (pixels[index] != targetColor) continue

            // Scan left and right horizontally to fill spans quickly
            var wx = x
            while (wx >= 0 && pixels[y * width + wx] == targetColor) {
                pixels[y * width + wx] = replacementColor
                wx--
            }

            var ex = x + 1
            while (ex < width && pixels[y * width + ex] == targetColor) {
                pixels[y * width + ex] = replacementColor
                ex++
            }

            // Check rows above and below the filled span
            for (i in (wx + 1) until ex) {
                if (y > 0 && pixels[(y - 1) * width + i] == targetColor) {
                    queue.add(Pair(i, y - 1))
                }
                if (y < height - 1 && pixels[(y + 1) * width + i] == targetColor) {
                    queue.add(Pair(i, y + 1))
                }
            }
        }

        // Apply modified pixels back to the bitmap
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
}