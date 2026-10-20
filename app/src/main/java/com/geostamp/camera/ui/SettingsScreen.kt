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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.geostamp.camera.data.CameraSettings
import com.geostamp.camera.data.DateFormat
import com.geostamp.camera.data.FontStyle
import com.geostamp.camera.data.Labeled
import com.geostamp.camera.data.MapPosition
import com.geostamp.camera.data.MapType
import com.geostamp.camera.data.SnapshotMode
import com.geostamp.camera.data.StampBackground
import com.geostamp.camera.data.StampColor
import com.geostamp.camera.data.StampDataRepository
import com.geostamp.camera.data.StampField
import com.geostamp.camera.data.StampGroup
import com.geostamp.camera.data.StampLanguage
import com.geostamp.camera.data.StampPosition
import com.geostamp.camera.data.StampSettings
import com.geostamp.camera.data.StampSize
import com.geostamp.camera.data.StampTemplate
import com.geostamp.camera.data.VideoCodec
import com.geostamp.camera.data.VolumeButtonAction
import com.geostamp.camera.data.WatermarkStyle
import com.geostamp.camera.data.FrameDataDensity
import com.geostamp.camera.stamp.FrameWatermarkRenderer
import com.geostamp.camera.stamp.PhotoComposer
import com.geostamp.camera.stamp.ShotParams
import com.geostamp.camera.stamp.WatermarkRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime

val Accent = Color(0xFFFFC107)
private val RowText = Color(0xFFEDEDED)
private val SubText = Color(0xFF9E9E9E)

@Composable
fun SettingsScreen(
    settings: StampSettings,
    repo: StampDataRepository,
    cam: CameraSettings? = null,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val data by repo.data.collectAsState()
    val config = settings.snapshot()
    var preview by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(data, config) {
        preview = withContext(Dispatchers.Default) {
            if (config.watermarkStyle != WatermarkStyle.CLASSIC_GEO_BADGE) {
                val dummy = Bitmap.createBitmap(1080, 810, Bitmap.Config.ARGB_8888).apply {
                    val canvas = android.graphics.Canvas(this)
                    val p = android.graphics.Paint().apply { color = android.graphics.Color.DKGRAY }
                    canvas.drawRect(0f, 0f, 1080f, 810f, p)
                }
                val sampleShot = ShotParams(focalMm = 26, aperture = 1.5f, shutterNs = 333333333L, iso = 12800, model = config.deviceBrand)
                val framed = FrameWatermarkRenderer.render(dummy, config, data, ZonedDateTime.now(), sampleShot)
                if (dummy !== framed && !dummy.isRecycled) dummy.recycle()
                framed
            } else {
                WatermarkRenderer.render(PhotoComposer.stampWidthFor(1080, 1440, StampSize.LARGE), config, data, ZonedDateTime.now(), "Back · Wide", preview = true)
            }
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
                        StampField.LAT_LONG -> if (settings.isEnabled(field)) {
                            ChoiceRow("Coordinate format", settings.coordinateFormat, com.geostamp.camera.data.CoordinateFormat.entries) { settings.coordinateFormat = it }
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
                        StampField.PROJECT -> if (settings.isEnabled(field)) {
                            InlineField("Project / Site name", settings.siteName) { settings.siteName = it }
                        }
                        StampField.PERSON_NAME -> if (settings.isEnabled(field)) {
                            InlineField("Person name", settings.personName) { settings.personName = it }
                        }
                        StampField.PHONE -> if (settings.isEnabled(field)) {
                            InlineField("Contact number", settings.phone, KeyboardType.Phone) { settings.phone = it }
                        }
                        StampField.CUSTOM_1 -> if (settings.isEnabled(field)) {
                            InlineField("Field 1 Label", settings.custom1Label) { settings.custom1Label = it }
                            InlineField("Field 1 Value", settings.custom1Value) { settings.custom1Value = it }
                        }
                        StampField.CUSTOM_2 -> if (settings.isEnabled(field)) {
                            InlineField("Field 2 Label", settings.custom2Label) { settings.custom2Label = it }
                            InlineField("Field 2 Value", settings.custom2Value) { settings.custom2Value = it }
                        }
                        else -> Unit
                    }
                  }
                }
            }

            item(key = "watermark_style_section") {
              Column {
                SectionHeader("Watermark & Photo Frame Style")
                ChoiceRow("Frame Style", settings.watermarkStyle, WatermarkStyle.entries) {
                    settings.watermarkStyle = it
                }
                if (settings.watermarkStyle != WatermarkStyle.CLASSIC_GEO_BADGE) {
                    ChoiceRow("Information Density", settings.frameDataDensity, FrameDataDensity.entries) {
                        settings.frameDataDensity = it
                    }
                    InlineField("Device Brand / Name", settings.deviceBrand) { settings.deviceBrand = it }
                    InlineField("Device Badge (e.g. Pro, Ultra)", settings.deviceBadge) { settings.deviceBadge = it }
                    InlineField("Custom Motto / Subtitle", settings.customMotto) { settings.customMotto = it }

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Show EXIF Parameters", color = RowText, fontSize = 16.sp)
                            Text("Focal length, aperture, shutter speed, ISO", color = SubText, fontSize = 12.sp)
                        }
                        Switch(
                            checked = settings.showExifParams,
                            onCheckedChange = { settings.showExifParams = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Accent),
                        )
                    }

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Show GPS & City on Frame", color = RowText, fontSize = 16.sp)
                            Text("Coordinates, altitude, and location name", color = SubText, fontSize = 12.sp)
                        }
                        Switch(
                            checked = settings.showGpsOnFrame,
                            onCheckedChange = { settings.showGpsOnFrame = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Accent),
                        )
                    }

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Show Avatar / Logo", color = RowText, fontSize = 16.sp)
                            Text("Custom logo or photographer profile silhouette", color = SubText, fontSize = 12.sp)
                        }
                        Switch(
                            checked = settings.showAvatarOnFrame,
                            onCheckedChange = { settings.showAvatarOnFrame = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Accent),
                        )
                    }
                }
              }
            }

            item(key = "stamp_settings") {
              Column {
                SectionHeader("Stamp Settings & Localization")
                ChoiceRow("Preset Template", StampTemplate.STANDARD, StampTemplate.entries) { settings.applyTemplate(it) }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text("Save / Load Custom Templates", color = RowText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..3).forEach { slot ->
                            val has = settings.hasCustomTemplate(slot)
                            OutlinedButton(
                                onClick = {
                                    if (has) {
                                        settings.loadCustomTemplate(slot)
                                        Toast.makeText(context, "Loaded Template $slot", Toast.LENGTH_SHORT).show()
                                    } else {
                                        settings.saveCustomTemplate(slot, "Template $slot")
                                        Toast.makeText(context, "Saved current setup to Template $slot", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (has) Accent else Color.White,
                                ),
                            ) {
                                Text(if (has) "Load $slot" else "Save $slot", fontSize = 12.sp)
                            }
                        }
                    }
                }
                ChoiceRow("Stamp Language", settings.language, StampLanguage.entries) { settings.language = it }
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
                if (cam != null) {
                    ToggleRow(
                        title = "Auto-Upload to Google Drive",
                        subtitle = "Prompts Google Drive upload after every capture",
                        checked = cam.autoDriveUpload,
                        onCheckedChange = { cam.autoDriveUpload = it },
                    )
                }
                Text(
                    "Map tiles © Esri and © OpenStreetMap contributors. Weather by Open-Meteo.com.",
                    color = SubText, fontSize = 11.sp, modifier = Modifier.padding(16.dp),
                )
                Spacer(Modifier.height(16.dp))
              }
            }

            if (cam != null) {
              item(key = "display_parameters_section") {
                Column {
                  SectionHeader("Display Parameters")
                  ToggleRow(
                      title = "Focus Peaking",
                      subtitle = "Highlight in-focus contrast edges with neon green lines in manual focus",
                      checked = cam.focusPeaking,
                      onCheckedChange = { cam.focusPeaking = it },
                  )
                  ToggleRow(
                      title = "Exposure Verification",
                      subtitle = "Zebra pattern: Blue for underexposed shadows & Red for overexposed highlights",
                      checked = cam.exposureVerification,
                      onCheckedChange = { cam.exposureVerification = it },
                  )
                  ToggleRow(
                      title = "Histogram",
                      subtitle = "Real-time luminance distribution graph on viewfinder",
                      checked = cam.histogram,
                      onCheckedChange = { cam.histogram = it },
                  )
                }
              }

              item(key = "video_settings_section") {
                Column {
                  SectionHeader("Video Settings")
                  ChoiceRow("Video Encoder", cam.videoCodec, VideoCodec.entries) { cam.videoCodec = it }
                  ChoiceRow("Take Photos While Recording", cam.snapshotMode, SnapshotMode.entries) { cam.snapshotMode = it }
                  ToggleRow(
                      title = "Auto Frame Rate",
                      subtitle = "Auto adjust 60 fps to 30/24 fps in low light or high heat",
                      checked = cam.autoFrameRate,
                      onCheckedChange = { cam.autoFrameRate = it },
                  )
                  ToggleRow(
                      title = "Allow Tagging Videos",
                      subtitle = "Show Tag button while recording to bookmark moments with JSON timestamps",
                      checked = cam.allowVideoTagging,
                      onCheckedChange = { cam.allowVideoTagging = it },
                  )
                  ToggleRow(
                      title = "Motion Tracking Focus",
                      subtitle = "Tap moving subject to continuously track and keep in focus",
                      checked = cam.motionTrackingFocus,
                      onCheckedChange = { cam.motionTrackingFocus = it },
                  )
                }
              }

              item(key = "preserve_settings_section") {
                Column {
                  SectionHeader("Preserve Settings")
                  Text(
                      text = "Retain previously used camera modes and parameters instead of automatically resetting to default choices when launching the camera.",
                      color = SubText,
                      fontSize = 12.sp,
                      modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                  )
                  ToggleRow(
                      title = "Camera Mode",
                      subtitle = "Preserve the previously used shooting mode (Photo/Video/Timelapse)",
                      checked = cam.preserveCameraMode,
                      onCheckedChange = { cam.preserveCameraMode = it },
                  )
                  ToggleRow(
                      title = "Portrait Focal Length",
                      subtitle = "Preserve the previously selected camera lens/focal length",
                      checked = cam.preservePortraitFocalLength,
                      onCheckedChange = { cam.preservePortraitFocalLength = it },
                  )
                  ToggleRow(
                      title = "Live Moments",
                      subtitle = "Preserve snapshot photo burst preference during recording",
                      checked = cam.preserveLiveMoments,
                      onCheckedChange = { cam.preserveLiveMoments = it },
                  )
                  ToggleRow(
                      title = "Beautify",
                      subtitle = "Preserve face smoothing and enhancement preferences",
                      checked = cam.preserveBeautify,
                      onCheckedChange = { cam.preserveBeautify = it },
                  )
                  ToggleRow(
                      title = "Filters",
                      subtitle = "Preserve color style and tone adjustments",
                      checked = cam.preserveFilters,
                      onCheckedChange = { cam.preserveFilters = it },
                  )
                  ToggleRow(
                      title = "AI Camera",
                      subtitle = "Preserve intelligent scene detection and OCR sign reading",
                      checked = cam.preserveAiCamera,
                      onCheckedChange = { cam.preserveAiCamera = it },
                  )
                  ToggleRow(
                      title = "Auto Night Mode",
                      subtitle = "Preserve low-light night mode extension choice",
                      checked = cam.preserveAutoNightMode,
                      onCheckedChange = { cam.preserveAutoNightMode = it },
                  )
                  ToggleRow(
                      title = "Exposure Compensation",
                      subtitle = "Preserve custom EV brightness offsets between launches",
                      checked = cam.preserveExposureCompensation,
                      onCheckedChange = { cam.preserveExposureCompensation = it },
                  )
                  ToggleRow(
                      title = "Parameters",
                      subtitle = "Preserve manual ISO, shutter speed, WB Kelvin, and focus distance",
                      checked = cam.preserveParameters,
                      onCheckedChange = { cam.preserveParameters = it },
                  )
                }
              }

              item(key = "hardware_buttons_section") {
                Column {
                  SectionHeader("Hardware Buttons")
                  ChoiceRow("Function of Volume Buttons", cam.volumeButtonAction, VolumeButtonAction.entries) {
                      cam.volumeButtonAction = it
                  }
                  Spacer(Modifier.height(32.dp))
                }
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
        StampField.QR_CODE -> "Signature"
        StampField.PROJECT -> settings.siteName.ifBlank { "-" }
        StampField.NOTE -> settings.note.ifBlank { "-" }
        StampField.PERSON_NAME -> settings.personName.ifBlank { "-" }
        StampField.PHONE -> settings.phone.ifBlank { "-" }
        StampField.CUSTOM_1 -> "${settings.custom1Label}: ${settings.custom1Value.ifBlank { "-" }}"
        StampField.CUSTOM_2 -> "${settings.custom2Label}: ${settings.custom2Value.ifBlank { "-" }}"
        StampField.TEMPERATURE -> f(d.temperatureC, "%.2f° C")
        StampField.WIND -> f(d.windKmh, "%.2f km/h")
        StampField.HUMIDITY -> d.humidity?.let { "$it%" } ?: "-"
        StampField.PRESSURE -> f(d.pressureHpa, "%.0f hpa")
        StampField.AQI -> d.aqi?.toString() ?: "-"
        StampField.SUN_TIMES -> if (d.sunrise != null && d.sunset != null) "${d.sunrise} / ${d.sunset}" else "-"
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
                Column(Modifier.verticalScroll(rememberScrollState())) {
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

@Composable
private fun ToggleRow(title: String, subtitle: String? = null, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = RowText, fontSize = 16.sp)
            if (subtitle != null) {
                Text(subtitle, color = SubText, fontSize = 12.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Accent),
        )
    }
}
