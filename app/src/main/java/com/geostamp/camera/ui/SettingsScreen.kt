package com.geostamp.camera.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.geostamp.camera.data.DateFormat
import com.geostamp.camera.data.FontStyle
import com.geostamp.camera.data.Labeled
import com.geostamp.camera.data.MapPosition
import com.geostamp.camera.data.MapType
import com.geostamp.camera.data.StampBackground
import com.geostamp.camera.data.StampColor
import com.geostamp.camera.data.StampDataRepository
import com.geostamp.camera.data.StampField
import com.geostamp.camera.data.StampGroup
import com.geostamp.camera.data.StampPosition
import com.geostamp.camera.data.StampSettings
import com.geostamp.camera.data.StampSize
import com.geostamp.camera.stamp.PhotoComposer
import com.geostamp.camera.stamp.WatermarkRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime

val Accent = Color(0xFFFFC107)
private val RowText = Color(0xFFEDEDED)
private val SubText = Color(0xFF9E9E9E)

@Composable
fun SettingsScreen(settings: StampSettings, repo: StampDataRepository, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val data by repo.data.collectAsState()
    val config = settings.snapshot()
    var preview by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(data, config) {
        preview = withContext(Dispatchers.Default) {
            WatermarkRenderer.render(PhotoComposer.stampWidthFor(1080, 1440, StampSize.LARGE), config, data, ZonedDateTime.now(), "Back · Wide", preview = true)
        }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) settings.setEnabled(StampField.SOUND_LEVEL, true)
        else Toast.makeText(context, "Microphone permission is needed for sound level", Toast.LENGTH_LONG).show()
    }
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val bmp = runCatching {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
            if (bmp != null) settings.setCustomLogo(bmp)
            else Toast.makeText(context, "Couldn't read that image", Toast.LENGTH_SHORT).show()
        }
    }

    fun toggle(field: StampField, on: Boolean) {
        if (field == StampField.SOUND_LEVEL && on &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        settings.setEnabled(field, on)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            Text("Stamp Template", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }

        // Live preview of the stamp
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF2B3A4A))
                .padding(8.dp),
        ) {
            preview?.let {
                Image(it.asImageBitmap(), "Stamp preview", contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth())
            } ?: Text("Everything is switched off", color = SubText, modifier = Modifier.padding(16.dp))
        }

        LazyColumn(Modifier.fillMaxSize()) {
            StampGroup.entries.forEach { group ->
                item(key = "h_${group.name}") { SectionHeader(group.title) }
                items(StampField.entries.filter { it.group == group }, key = { it.key }) { field ->
                  Column {
                    FieldRow(settings, field, data, onToggle = { on -> toggle(field, on) })
                    when (field) {
                        StampField.MAP -> if (settings.isEnabled(field)) {
                            ChoiceRow("Map Type", settings.mapType, MapType.entries) { settings.mapType = it }
                        }
                        StampField.DATE_TIME -> if (settings.isEnabled(field)) {
                            ChoiceRow("Date format", settings.dateFormat, DateFormat.entries) { settings.dateFormat = it }
                        }
                        StampField.NUMBERING -> if (settings.isEnabled(field)) {
                            InlineField("Next photo number", settings.nextNumber.toString(), KeyboardType.Number) { v ->
                                v.filter { it.isDigit() }.take(9).toIntOrNull()?.let { settings.nextNumber = it }
                            }
                        }
                        StampField.LOGO -> if (settings.isEnabled(field)) {
                            Row(
                                Modifier.padding(start = 56.dp, end = 16.dp, bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                TextButton(onClick = {
                                    logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }) { Text("Choose logo", color = Accent) }
                                if (settings.hasCustomLogo) {
                                    TextButton(onClick = { settings.resetLogo() }) { Text("Use default", color = SubText) }
                                }
                            }
                        }
                        StampField.NOTE -> if (settings.isEnabled(field)) {
                            InlineField("Note / #hashtag", settings.note) { settings.note = it }
                        }
                        StampField.PERSON_NAME -> if (settings.isEnabled(field)) {
                            InlineField("Person name", settings.personName) { settings.personName = it }
                        }
                        StampField.PHONE -> if (settings.isEnabled(field)) {
                            InlineField("Contact number", settings.phone, KeyboardType.Phone) { settings.phone = it }
                        }
                        else -> Unit
                    }
                  }
                }
            }

            item(key = "stamp_settings") {
              Column {
                SectionHeader("Stamp Settings")
                ChoiceRow("Font Style", settings.font, FontStyle.entries) { settings.font = it }
                ChoiceRow("Stamp Color", settings.color, StampColor.entries) { settings.color = it }
                ChoiceRow("Background", settings.background, StampBackground.entries) { settings.background = it }
                ChoiceRow("Stamp Size", settings.size, StampSize.entries) { settings.size = it }
                ChoiceRow("Stamp Position", settings.position, StampPosition.entries) { settings.position = it }
                ChoiceRow("Map Position", settings.mapPosition, MapPosition.entries) { settings.mapPosition = it }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row {
                        Text("Map Scale", color = RowText, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Text("${settings.mapScalePercent}%", color = SubText)
                    }
                    Slider(
                        value = settings.mapScalePercent.toFloat(),
                        onValueChange = { settings.mapScalePercent = it.toInt() },
                        valueRange = 40f..100f,
                    )
                }
                Text(
                    "Map tiles © Esri and © OpenStreetMap contributors. Weather by Open-Meteo.com.",
                    color = SubText, fontSize = 11.sp, modifier = Modifier.padding(16.dp),
                )
                Spacer(Modifier.height(32.dp))
              }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title, color = Accent, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 6.dp),
    )
}

/** Current value shown on the right of each row, like the reference app. */
private fun valueFor(field: StampField, settings: StampSettings, d: com.geostamp.camera.data.StampData): String {
    fun f(v: Double?, fmt: String) = v?.let { String.format(java.util.Locale.US, fmt, it) } ?: "-"
    return when (field) {
        StampField.MAP -> settings.mapType.label
        StampField.TITLE -> d.title ?: "-"
        StampField.COUNTRY_FLAG -> WatermarkRenderer.flagEmoji(d.countryCode)
        StampField.SHORT_ADDRESS -> d.shortAddress ?: "-"
        StampField.ADDRESS -> d.address ?: "-"
        StampField.LAT_LONG -> if (d.latitude != null) f(d.latitude, "%.4f") + ", " + f(d.longitude, "%.4f") else "-"
        StampField.PLUS_CODE -> d.plusCode ?: "-"
        StampField.ALTITUDE -> f(d.altitude, "%.0f m")
        StampField.ACCURACY -> d.accuracyM?.let { String.format(java.util.Locale.US, "%.0f m", it) } ?: "-"
        StampField.DATE_TIME -> settings.dateFormat.label
        StampField.TIME_ZONE -> "GMT " + java.time.format.DateTimeFormatter.ofPattern("xxx").format(ZonedDateTime.now())
        StampField.NUMBERING -> settings.nextNumber.toString()
        StampField.LOGO -> if (settings.hasCustomLogo) "Custom" else "Default"
        StampField.NOTE -> settings.note.ifBlank { "-" }
        StampField.PERSON_NAME -> settings.personName.ifBlank { "-" }
        StampField.PHONE -> settings.phone.ifBlank { "-" }
        StampField.TEMPERATURE -> f(d.temperatureC, "%.2f° C")
        StampField.WIND -> f(d.windKmh, "%.2f km/h")
        StampField.HUMIDITY -> d.humidity?.let { "$it%" } ?: "-"
        StampField.PRESSURE -> f(d.pressureHpa, "%.0f hpa")
        StampField.COMPASS -> d.azimuthDeg?.let { String.format(java.util.Locale.US, "%.0f° %s", it, WatermarkRenderer.direction(it)) } ?: "-"
        StampField.MAGNETIC -> d.magneticUt?.let { String.format(java.util.Locale.US, "%.2f µT", it) } ?: "-"
        StampField.SOUND_LEVEL -> d.soundDb?.let { String.format(java.util.Locale.US, "%.1f dB", it) } ?: "-"
        StampField.CAMERA_INFO -> "Lens name"
    }
}

@Composable
private fun FieldRow(settings: StampSettings, field: StampField, data: com.geostamp.camera.data.StampData, onToggle: (Boolean) -> Unit) {
    val on = settings.isEnabled(field)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onToggle(!on) }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (on) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            contentDescription = if (on) "On" else "Off",
            tint = if (on) Accent else Color(0xFF616161),
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(field.label, color = RowText, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(
            valueFor(field, settings, data), color = SubText, fontSize = 13.sp, maxLines = 1,
            modifier = Modifier.width(130.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

@Composable
private fun InlineField(label: String, value: String, keyboard: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        colors = darkFieldColors(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 56.dp, end = 16.dp, bottom = 8.dp),
    )
}

@Composable
fun darkFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = Accent,
    unfocusedBorderColor = Color(0xFF555555),
    focusedLabelColor = Accent,
    unfocusedLabelColor = SubText,
    cursorColor = Accent,
)

@Composable
fun <T : Labeled> ChoiceRow(title: String, value: T, options: List<T>, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { open = true }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = RowText, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(value.label, color = SubText, fontSize = 13.sp, maxLines = 1)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SubText)
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column {
                    options.forEach { opt ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelect(opt); open = false }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = opt == value, onClick = { onSelect(opt); open = false })
                            Text(opt.label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } },
        )
    }
}
