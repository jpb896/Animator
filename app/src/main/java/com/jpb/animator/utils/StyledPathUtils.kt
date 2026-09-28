package com.jpb.animator.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

enum class DrawStyle {
    STROKE, FILL
}

data class StyledPath(
    val path: Path,
    val color: Color,
    val strokeWidth: Float,
    val drawStyle: DrawStyle
)