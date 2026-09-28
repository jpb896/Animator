package com.jpb.animator.utils

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import java.io.Serializable

enum class DrawStyle {
    STROKE, FILL
}

// A serializable representation of a point
data class SerializableOffset(val x: Float, val y: Float) : Serializable {
    constructor(offset: Offset) : this(offset.x, offset.y)
    val toOffset: Offset get() = Offset(x, y)
}

data class StyledPath(
    val colorInt: Int,
    val strokeWidth: Float,
    val drawStyle: DrawStyle,
    val points: List<SerializableOffset>
) : Serializable {

    @Transient
    var path: Path = Path()
        private set

    // Added `= emptyList()` so it won't break calls missing points
    constructor(
        rawPath: Path = Path(),
        rawPoints: List<Offset> = emptyList(),
        color: Color,
        strokeWidth: Float,
        drawStyle: DrawStyle
    ) : this(
        colorInt = color.toArgb(),
        strokeWidth = strokeWidth,
        drawStyle = drawStyle,
        points = rawPoints.map { SerializableOffset(it) }
    ) {
        this.path = rawPath
    }

    val color: Color
        get() = Color(colorInt)

    private fun readObject(inStream: java.io.ObjectInputStream) {
        inStream.defaultReadObject()
        path = Path().apply {
            points.forEachIndexed { index, pt ->
                if (index == 0) moveTo(pt.x, pt.y)
                else lineTo(pt.x, pt.y)
            }
        }
    }
}