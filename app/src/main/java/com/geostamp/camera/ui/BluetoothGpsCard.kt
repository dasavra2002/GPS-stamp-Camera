package com.geostamp.camera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.geostamp.camera.location.BluetoothGpsManager
import com.geostamp.camera.location.BluetoothGpsState
import java.util.Locale

@Composable
fun BluetoothGpsSection(
    manager: BluetoothGpsManager,
    modifier: Modifier = Modifier,
) {
    val connectionState = manager.connectionState.value
    val connectedDevice = manager.connectedDevice.value
    val loc = manager.lastLocation.value
    val error = manager.errorMessage.value

    var pairedDevices by remember { mutableStateOf(manager.getPairedDevices()) }

    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        if (!manager.isSupported) {
            StatusLine(
                Icons.Filled.BluetoothDisabled,
                "This device has no Bluetooth.",
                MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }

        if (!manager.isEnabled) {
            StatusLine(
                Icons.Filled.BluetoothDisabled,
                "Bluetooth is off. Turn it on in system settings to use an external receiver.",
                MaterialTheme.colorScheme.error,
            )
            return
        }

        when (connectionState) {
            BluetoothGpsState.DISCONNECTED -> {
                Text(
                    "Connect a Bluetooth GNSS or RTK receiver (Garmin GLO, Bad Elf, Trimble, Emlid Reach, ArduSimple) for centimeter-level accuracy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Paired receivers",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(onClick = { pairedDevices = manager.getPairedDevices() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh paired devices")
                    }
                }

                if (pairedDevices.isEmpty()) {
                    Text(
                        "No paired devices. Pair your receiver in Android Bluetooth settings, then tap refresh.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    pairedDevices.forEachIndexed { i, device ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DeviceAvatar(Icons.Filled.Bluetooth, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    device.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    device.address,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            FilledTonalButton(onClick = { manager.connect(device) }) {
                                Text("Connect")
                            }
                        }
                    }
                }
            }

            BluetoothGpsState.CONNECTING -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        "Connecting to receiver…",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { manager.disconnect() }) {
                        Text("Cancel")
                    }
                }
            }

            BluetoothGpsState.CONNECTED -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DeviceAvatar(Icons.Filled.BluetoothConnected, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            connectedDevice?.name ?: "External GNSS",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            connectedDevice?.address ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { manager.disconnect() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Disconnect")
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Live GNSS telemetry
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.medium)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (loc != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = if (loc.isRtk) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                                shape = CircleShape
                            ) {
                                Text(
                                    loc.fixQuality.label,
                                    color = if (loc.isRtk) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.SatelliteAlt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "${loc.satellitesInUse} sats · HDOP ${loc.hdop?.let { String.format(Locale.US, "%.2f", it) } ?: "-"}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (loc.latitude != null && loc.longitude != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Telemetry("Latitude", String.format(Locale.US, "%.7f°", loc.latitude), Modifier.weight(1f))
                                Telemetry("Longitude", String.format(Locale.US, "%.7f°", loc.longitude), Modifier.weight(1f))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Telemetry(
                                "Altitude (MSL)",
                                loc.altitudeM?.let { String.format(Locale.US, "%.2f m", it) } ?: "-",
                                Modifier.weight(1f)
                            )
                            Telemetry(
                                "Accuracy",
                                loc.accuracyM?.let { String.format(Locale.US, "±%.2f m", it) } ?: "-",
                                Modifier.weight(1f),
                                valueColor = loc.accuracyM?.let {
                                    if (it <= 0.1f) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                                } ?: MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Waiting for the first position from the receiver…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (error != null) {
            Spacer(Modifier.height(8.dp))
            StatusLine(Icons.Filled.ErrorOutline, error, MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun StatusLine(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@Composable
private fun DeviceAvatar(icon: ImageVector, container: Color, content: Color) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(container, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Telemetry(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = valueColor,
            maxLines = 1
        )
    }
}
