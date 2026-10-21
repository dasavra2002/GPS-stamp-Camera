package com.geostamp.camera.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.geostamp.camera.data.GeofenceSite
import java.util.Locale

@Composable
fun AddGeofenceSiteDialog(
    currentLat: Double?,
    currentLon: Double?,
    onDismiss: () -> Unit,
    onSave: (GeofenceSite) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var latStr by remember { mutableStateOf(currentLat?.let { String.format(Locale.US, "%.6f", it) } ?: "") }
    var lonStr by remember { mutableStateOf(currentLon?.let { String.format(Locale.US, "%.6f", it) } ?: "") }
    var radiusM by remember { mutableFloatStateOf(250f) }
    var contractor by remember { mutableStateOf("") }
    var client by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val radiusOptions = listOf(100f, 250f, 500f, 1000f)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Place, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add Project Site Geofence", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Project / Site Name") },
                    placeholder = { Text("e.g. Metro Line 3 Pier 42") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = latStr,
                        onValueChange = { latStr = it },
                        label = { Text("Latitude") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = lonStr,
                        onValueChange = { lonStr = it },
                        label = { Text("Longitude") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (currentLat != null && currentLon != null) {
                    OutlinedButton(
                        onClick = {
                            latStr = String.format(Locale.US, "%.6f", currentLat)
                            lonStr = String.format(Locale.US, "%.6f", currentLon)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.MyLocation, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Use Current GPS Location")
                    }
                }

                Text("Geofence Radius", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    radiusOptions.forEach { r ->
                        FilterChip(
                            selected = radiusM == r,
                            onClick = { radiusM = r },
                            label = { Text("${r.toInt()}m") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                OutlinedTextField(
                    value = contractor,
                    onValueChange = { contractor = it },
                    label = { Text("Contractor / Agency (Auto-tag)") },
                    placeholder = { Text("e.g. L&T Infrastructure") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = client,
                    onValueChange = { client = it },
                    label = { Text("Client / Department (Auto-tag)") },
                    placeholder = { Text("e.g. Municipal Corp") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Site Note / Project Code") },
                    placeholder = { Text("e.g. #BridgeSurvey") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val lat = latStr.toDoubleOrNull() ?: 0.0
                    val lon = lonStr.toDoubleOrNull() ?: 0.0
                    if (name.isNotBlank() && lat != 0.0 && lon != 0.0) {
                        val site = GeofenceSite(
                            name = name.trim(),
                            latitude = lat,
                            longitude = lon,
                            radiusMeters = radiusM,
                            contractor = contractor.trim(),
                            client = client.trim(),
                            note = note.trim(),
                            autoApply = true
                        )
                        onSave(site)
                    }
                },
                enabled = name.isNotBlank() && latStr.toDoubleOrNull() != null && lonStr.toDoubleOrNull() != null
            ) {
                Text("Add Site")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
