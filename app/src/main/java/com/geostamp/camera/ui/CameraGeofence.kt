package com.geostamp.camera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.geostamp.camera.ui.theme.CameraColors

/**
 * Whether the phone is inside a saved site (or the manually pinned one). A pill under the top bar
 * in landscape, a full-width banner in portrait.
 */
@Composable
internal fun GeofenceSection(
    vm: CameraViewModel,
    currentLat: Double?,
    currentLon: Double?,
) {
    val geoMgr = vm.geofences
    val settings = vm.settings
    val activeSitePair = remember(currentLat, currentLon, geoMgr.sites.size) {
        geoMgr.findActiveSite(currentLat, currentLon)
    }
    val nearestSitePair = remember(currentLat, currentLon, geoMgr.sites.size) {
        geoMgr.findNearestSite(currentLat, currentLon)
    }
    val pinHere = { vm.pinSiteHere(currentLat, currentLon) }
    val siteLat = settings.siteLat
    val siteLon = settings.siteLon
    val siteRadius = settings.siteRadiusM
    val siteName = settings.siteName
    val pinnedDistanceM: Float? = remember(currentLat, currentLon, siteLat, siteLon) {
        if (currentLat != null && currentLon != null && siteLat != null && siteLon != null) {
            val results = FloatArray(1)
            android.location.Location.distanceBetween(currentLat, currentLon, siteLat, siteLon, results)
            results[0]
        } else null
    }

    if (activeSitePair != null) {
        val (site, dist) = activeSitePair
        GeofenceBanner(
            text = "In ${site.name} · ${dist.toInt()} m",
            icon = Icons.Filled.CheckCircle,
            container = CameraColors.InsideFence,
            content = CameraColors.OnScrim,
            action = "AUTO-TAGGED",
            actionColor = CameraColors.InsideFenceAccent,
        )
    } else if (nearestSitePair != null) {
        val (site, dist) = nearestSitePair
        GeofenceBanner(
            text = "Outside ${site.name} · ${dist.toInt()} m (limit ${site.radiusMeters.toInt()} m)",
            icon = Icons.Filled.Warning,
            container = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f),
            content = MaterialTheme.colorScheme.onErrorContainer,
        )
    } else if (pinnedDistanceM != null) {
        val outside = pinnedDistanceM > siteRadius
        GeofenceBanner(
            text = if (outside) "Outside ${siteName.ifBlank { "site" }} · ${pinnedDistanceM.toInt()} m (limit ${siteRadius.toInt()} m)"
            else "In site geofence · ${pinnedDistanceM.toInt()} m",
            icon = if (outside) Icons.Filled.Warning else Icons.Filled.CheckCircle,
            container = if (outside) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f) else CameraColors.InsideFence,
            content = if (outside) MaterialTheme.colorScheme.onErrorContainer else CameraColors.OnScrim,
            action = "PIN HERE",
            onAction = pinHere,
        )
    } else if (siteName.isNotBlank() && currentLat != null && currentLon != null && siteLat == null) {
        GeofenceBanner(
            text = "Site: $siteName (no pin)",
            icon = Icons.Filled.Place,
            container = CameraColors.ScrimStrong,
            content = CameraColors.OnScrim,
            action = "SET PIN",
            onAction = pinHere,
        )
    }
}

@Composable
private fun GeofenceBanner(
    text: String,
    icon: ImageVector,
    container: Color,
    content: Color,
    action: String? = null,
    actionColor: Color = MaterialTheme.colorScheme.primary,
    onAction: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(container)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
        Text(
            text,
            color = content,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            Text(
                action,
                color = actionColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = if (onAction != null) {
                    Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onAction)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                } else Modifier,
            )
        }
    }
}
