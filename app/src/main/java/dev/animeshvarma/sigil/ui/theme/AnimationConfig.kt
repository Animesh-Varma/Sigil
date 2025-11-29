package dev.animeshvarma.sigil.ui.theme

import androidx.compose.animation.core.Spring

object AnimationConfig {
    // Tweak these to change the "feel" of the app
    const val STIFFNESS = Spring.StiffnessMediumLow // Lower = Slower/Softer
    const val DAMPING = 0.75f // 1.0 = No Bounce, 0.5 = High Bounce, 0.75 = Subtle Bounce
}