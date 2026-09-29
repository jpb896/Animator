package com.jpb.animator.utils

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Path
import java.io.Serializable

data class Layer(
    val name: String,
    val paths: List<StyledPath> = emptyList(),
    @Transient var rasterBitmap: Bitmap? = null, // @Transient prevents crashing on non-serializable Bitmap objects
    val isVisible: Boolean = true
) : Serializable

data class AnimationFrame(
    val layers: List<Layer> = listOf(Layer(name = "Layer 1"))
) : Serializable

fun duplicateCurrentFrame(
    frames: List<AnimationFrame>,
    currentIndex: Int
): List<AnimationFrame> {
    if (currentIndex !in frames.indices) return frames

    val currentFrame = frames[currentIndex]

    // Deep copy layers and their paths using direct constructor instantiation
    val duplicatedLayers = currentFrame.layers.map { layer ->
        layer.copy(
            name = "${layer.name} (Copy)",
            rasterBitmap = layer.rasterBitmap?.copy(Bitmap.Config.ARGB_8888, true),
            paths = layer.paths.map { styledPath ->
                StyledPath(
                    rawPath = Path().apply { addPath(styledPath.path) },
                    rawPoints = mutableListOf(), // or copy points if your project tracks them
                    color = styledPath.color,
                    strokeWidth = styledPath.strokeWidth,
                    drawStyle = styledPath.drawStyle
                )
            }.toMutableList()
        )
    }

    val newFrame = AnimationFrame(layers = duplicatedLayers)
    val mutableFrames = frames.toMutableList()
    mutableFrames.add(currentIndex + 1, newFrame)

    return mutableFrames
}