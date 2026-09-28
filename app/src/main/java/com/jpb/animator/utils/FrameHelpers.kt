package com.jpb.animator.utils

import android.graphics.Bitmap

data class Layer(
    val name: String,
    val paths: List<StyledPath> = emptyList(),
    val rasterBitmap: Bitmap? = null,
    val isVisible: Boolean = true
)

data class AnimationFrame(
    val layers: List<Layer> = listOf(Layer(name = "Layer 1"))
)