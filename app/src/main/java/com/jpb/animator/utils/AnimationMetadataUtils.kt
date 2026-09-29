package com.jpb.animator.utils

import java.io.Serializable

data class AnimationProject(
    var title: String = "My Animation",
    var fps: Int = 12,                     // Independent FPS per project
    var width: Int = 1080,                 // Independent width per project
    var height: Int = 1080,                // Independent height per project
    var backgroundColorInt: Int = android.graphics.Color.WHITE, // Independent background color
    var layers: List<Layer> = emptyList()  // Your existing layers
) : Serializable {

    // Convenience getter/setter for Compose Color if needed
    val backgroundColor: androidx.compose.ui.graphics.Color
        get() = androidx.compose.ui.graphics.Color(backgroundColorInt)
}