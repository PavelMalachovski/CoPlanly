package com.coparently.app.utils

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec

object AnimationConstants {
    // Velocities of animations by type
    const val INSTANT = 0
    const val FAST = 150
    const val NORMAL = 300
    const val SLOW = 500

    // Spring constants for Material Design
    val SPRING_BOUNCY = SpringSpec<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )

    val SPRING_STIFF = SpringSpec<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh
    )
}
