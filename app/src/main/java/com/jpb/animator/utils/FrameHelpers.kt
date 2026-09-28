package com.jpb.animator.utils

import android.graphics.Bitmap
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