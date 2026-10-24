package com.geostamp.camera.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints

// The camera screen stays in portrait, like the stock camera app; when the phone is turned
// sideways only the icons and labels turn, so they read upright while the layout doesn't jump.

/** Angle (degrees, animated) that keeps camera icons upright. */
internal val LocalIconRotation = compositionLocalOf<State<Float>> { mutableStateOf(0f) }

/** How far the phone is turned clockwise: 0, 90, 180 or 270. */
internal val LocalDeviceTurn = compositionLocalOf { 0 }

/** Animated counter-rotation for [deviceTurn], always turning the short way round. */
@Composable
internal fun rememberIconRotation(deviceTurn: Int): State<Float> {
    var target by remember { mutableFloatStateOf(-deviceTurn.toFloat()) }
    LaunchedEffect(deviceTurn) {
        val delta = ((-deviceTurn - target) % 360f + 540f) % 360f - 180f
        target += delta
    }
    return animateFloatAsState(target, tween(durationMillis = 250), label = "iconRotation")
}

/** Turns a roughly square control (icon, thumbnail, tile) so it reads upright. */
@Composable
internal fun Modifier.upright(): Modifier {
    val rotation = LocalIconRotation.current
    return graphicsLayer { rotationZ = rotation.value }
}

/**
 * Turns a wide control (a pill or a label) upright by quarter turns, swapping its width and
 * height in the layout so it doesn't overlap its neighbours when turned on its side.
 */
@Composable
internal fun Modifier.uprightQuarter(): Modifier = quarterTurns(LocalDeviceTurn.current)

/** Lays the content out turned counter-clockwise by [deviceTurn] degrees (a multiple of 90). */
internal fun Modifier.quarterTurns(deviceTurn: Int): Modifier {
    val turn = ((deviceTurn % 360) + 360) % 360
    if (turn == 0) return this
    val sideways = turn == 90 || turn == 270
    return layout { measurable, constraints ->
        val c = if (sideways) {
            Constraints(constraints.minHeight, constraints.maxHeight, constraints.minWidth, constraints.maxWidth)
        } else constraints
        val p = measurable.measure(c)
        val w = if (sideways) p.height else p.width
        val h = if (sideways) p.width else p.height
        layout(w, h) {
            p.placeWithLayer((w - p.width) / 2, (h - p.height) / 2) { rotationZ = -turn.toFloat() }
        }
    }
}
