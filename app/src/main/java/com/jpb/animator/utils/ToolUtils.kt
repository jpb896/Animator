package com.jpb.animator.utils

import androidx.compose.ui.graphics.Color

enum class ToolType {
    PEN, ERASER, FILL
}

data class DrawingToolState(
    val toolType: ToolType = ToolType.PEN,
    val currentColor: Color = Color.Black,
    val strokeWidth: Float = 8f
)