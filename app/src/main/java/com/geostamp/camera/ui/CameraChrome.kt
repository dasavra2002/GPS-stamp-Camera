package com.geostamp.camera.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.geostamp.camera.ui.theme.CameraColors

// Shared building blocks for controls drawn over the viewfinder, so every button, chip and
// pill on the camera screen has the same size, scrim and active state.

/** Round translucent icon button. 44dp keeps it a comfortable touch target. */
@Composable
fun CameraIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
) {
    Surface(
        shape = CircleShape,
        color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else CameraColors.Scrim,
        border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else CameraColors.ScrimBorder),
        modifier = modifier.size(size),
    ) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxSize()) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = when {
                    !enabled -> CameraColors.OnScrimDisabled
                    active -> MaterialTheme.colorScheme.primary
                    else -> CameraColors.OnScrim
                },
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

/** Toggle chip for the quick tools strip and the timer/burst cyclers. */
@Composable
fun CameraChip(
    text: String,
    on: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = when {
            on && enabled -> MaterialTheme.colorScheme.primary
            else -> CameraColors.Scrim
        },
        border = BorderStroke(1.dp, if (on && enabled) MaterialTheme.colorScheme.primary else CameraColors.ScrimBorder),
        modifier = modifier,
    ) {
        val fg = when {
            !enabled -> CameraColors.OnScrimDisabled
            on -> MaterialTheme.colorScheme.onPrimary
            else -> CameraColors.OnScrim
        }
        Row(
            Modifier.padding(start = if (icon != null) 10.dp else 12.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
            Text(text, color = fg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** Small status pill: a colored dot or icon followed by a short label. */
@Composable
fun CameraPill(
    text: String,
    modifier: Modifier = Modifier,
    dotColor: Color? = null,
    icon: ImageVector? = null,
    iconTint: Color = CameraColors.OnScrim,
    container: Color = CameraColors.Scrim,
    contentColor: Color = CameraColors.OnScrim,
    onClick: (() -> Unit)? = null,
) {
    val content: @Composable () -> Unit = {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (dotColor != null) {
                Surface(shape = CircleShape, color = dotColor, modifier = Modifier.size(8.dp)) {}
            }
            if (icon != null) Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(14.dp))
            Text(
                text,
                color = contentColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    val border = BorderStroke(1.dp, CameraColors.ScrimBorder)
    if (onClick != null) {
        Surface(onClick = onClick, shape = CircleShape, color = container, border = border, modifier = modifier, content = content)
    } else {
        Surface(shape = CircleShape, color = container, border = border, modifier = modifier, content = content)
    }
}

/**
 * Pill-shaped segmented selector used for lenses and zoom presets. [selected] may be -1 when
 * no segment matches (e.g. pinch zoom between presets).
 */
@Composable
fun CameraSegmented(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        shape = CircleShape,
        color = CameraColors.Scrim,
        border = BorderStroke(1.dp, CameraColors.ScrimBorder),
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            labels.forEachIndexed { i, label ->
                val active = i == selected
                Surface(
                    onClick = { onSelect(i) },
                    enabled = enabled,
                    shape = CircleShape,
                    color = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                    modifier = Modifier.defaultMinSize(minWidth = 40.dp, minHeight = 32.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Text(
                            label,
                            color = when {
                                active -> MaterialTheme.colorScheme.onPrimary
                                enabled -> CameraColors.OnScrim
                                else -> CameraColors.OnScrimDisabled
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** Text-only capture mode switcher, like the stock camera app: active mode in the accent color. */
@Composable
fun CameraModeLabel(
    text: String,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 12.dp,
) {
    Column(
        modifier
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = horizontalPadding, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text,
            color = if (active) MaterialTheme.colorScheme.primary else CameraColors.OnScrimMuted,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent),
        )
    }
}
