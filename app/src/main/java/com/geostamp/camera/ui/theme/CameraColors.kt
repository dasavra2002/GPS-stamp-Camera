package com.geostamp.camera.ui.theme

import androidx.compose.ui.graphics.Color

// Fixed colors for chrome drawn over the live viewfinder. These stay the same in every theme
// so controls read the same against any scene, and so status colors keep their meaning.
object CameraColors {
    val Scrim = Color(0x99000000)
    val ScrimStrong = Color(0xCC000000)
    val ScrimBorder = Color(0x33FFFFFF)
    val OnScrim = Color.White
    val OnScrimMuted = Color(0xB3FFFFFF)
    val OnScrimDisabled = Color(0x61FFFFFF)

    val Recording = Color(0xFFFF3B30)
    val SlowMo = Color(0xFF40C4FF)
    val GoodFix = Color(0xFF34C759)
    val WeakFix = Color(0xFFFFB300)
    val BadFix = Color(0xFFFF3B30)

    val InsideFence = Color(0xE01B5E20)
    val InsideFenceAccent = Color(0xFFA5D6A7)
    val DocumentGuide = Color(0xFF81C784)
    val ErrorScrim = Color(0xE0B71C1C)
}
