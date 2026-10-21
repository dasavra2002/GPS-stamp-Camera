package com.geostamp.camera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.geostamp.camera.location.BluetoothDeviceInfo
import com.geostamp.camera.location.BluetoothGpsManager
import com.geostamp.camera.location.BluetoothGpsState
import com.geostamp.camera.location.NmeaFixQuality
import java.util.Locale

@Composable
fun BluetoothGpsSection(
    manager: BluetoothGpsManager,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val connectionState = manager.connectionState.value
    val connectedDevice = manager.connectedDevice.value
    val loc = manager.lastLocation.value
    val error = manager.errorMessage.value

    var pairedDevices by remember { mutableStateOf(manager.getPairedDevices()) }

    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        if (!manager.isSupported) {
            Text(
                "Bluetooth hardware is not supported on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return
        }

        if (!manager.isEnabled) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.BluetoothDisabled, null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Bluetooth is turned off. Please turn on Bluetooth in device settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            return
        }

        when (connectionState) {
            BluetoothGpsState.DISCONNECTED -> {
                Text(
                    "Connect an external Bluetooth surveying GNSS/RTK receiver (Garmin GLO, Bad Elf, Trimble, Emlid Reach, ArduSimple) for centimeter-level accuracy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Paired Bluetooth Receivers", style = MaterialTheme.typography.labelLarge)
                    IconButton(onClick = { pairedDevices = manager.getPairedDevices() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh paired devices")
                    }
                }

                if (pairedDevices.isEmpty()) {
                    Text(
                        "No paired Bluetooth devices found. Pair your GNSS receiver in Android Settings first, then tap Refresh.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    pairedDevices.forEach { device ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(device.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(device.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            Button(
                                onClick = { manager.connect(device) },
                                shape = MaterialTheme.shapes.small
                            ) {
                                Icon(Icons.Filled.Bluetooth, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Connect")
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                }
            }

            BluetoothGpsState.CONNECTING -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Connecting to receiver…", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { manager.disconnect() }) {
                        Text("Cancel")
                    }
                }
            }

            BluetoothGpsState.CONNECTED -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.BluetoothConnected, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    connectedDevice?.name ?: "External GNSS",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    connectedDevice?.address ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = { manager.disconnect() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text("Disconnect")
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(10.dp))

                    // Live GNSS Telemetry Box
                    if (loc != null) {
                        val isRtk = loc.isRtk
                        val badgeBg = if (isRtk) Color(0xFFE8F5E9) else Color(0xFFE3F2FD)
                        val badgeFg = if (isRtk) Color(0xFF2E7D32) else Color(0xFF1565C0)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(color = badgeBg, shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    loc.fixQuality.label,
                                    color = badgeFg,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            Text(
                                "🛰️ ${loc.satellitesInUse} Sats • HDOP: ${loc.hdop?.let { String.format(Locale.US, "%.2f", it) } ?: "-"}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        if (loc.latitude != null && loc.longitude != null) {
                            Text(
                                String.format(Locale.US, "Lat: %.7f°, Lon: %.7f°", loc.latitude, loc.longitude),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        loc.altitudeM?.let {
                            Text(
                                String.format(Locale.US, "MSL Altitude: %.2f m", it),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        loc.accuracyM?.let {
                            Text(
                                String.format(Locale.US, "Estimated Accuracy: ±%.2f m", it),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (it <= 0.1f) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Text(
                            "Waiting for initial NMEA sentences from receiver…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (error != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
