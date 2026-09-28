package com.jpb.animator.utils

data class Layer(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val paths: List<StyledPath> = emptyList(),
    val isVisible: Boolean = true
)

data class AnimationFrame(
    val layers: List<Layer> = listOf(Layer(name = "Layer 1"))
)