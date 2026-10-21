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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Circle
import com.geostamp.camera.data.GeofenceManager
import com.geostamp.camera.data.GeofenceSite
import com.geostamp.camera.location.BluetoothGpsManager
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.geostamp.camera.data.OfflineMapManager
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime

val Accent = Color(0xFFFFC107)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: StampSettings,
    repo: StampDataRepository,
    cam: CameraSettings? = null,
    geofenceManager: GeofenceManager? = null,
    bluetoothGpsManager: BluetoothGpsManager? = null,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val geoMgr = geofenceManager ?: remember { GeofenceManager(context) }
    val btManager = bluetoothGpsManager ?: remember { BluetoothGpsManager(context, scope, repo) }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Stamp Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. Live preview card
            item(key = "live_preview_card") {
                ElevatedCard(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                        ) {
                            Text(
                                "LIVE WATERMARK PREVIEW",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                            )
                            Text(
                                settings.watermarkStyle.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                            contentAlignment = Alignment.Center,
                        ) {
                            preview?.let {
                                Image(
                                    it.asImageBitmap(),
                                    contentDescription = "Stamp preview",
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } ?: Text(
                                "Everything is switched off",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                    }
                }
            }

            // 2. Watermark & Frame Style Card
            item(key = "watermark_style_section") {
                SettingsGroupCard(title = "Watermark & Photo Frame Style") {
                    ChoiceRow("Frame Style", settings.watermarkStyle, WatermarkStyle.entries) {
                        settings.watermarkStyle = it
                    }
                    if (settings.watermarkStyle != WatermarkStyle.CLASSIC_GEO_BADGE) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ChoiceRow("Information Density", settings.frameDataDensity, FrameDataDensity.entries) {
                            settings.frameDataDensity = it
                        }
                        InlineField("Device Brand / Name", settings.deviceBrand) { settings.deviceBrand = it }
                        InlineField("Device Badge (e.g. Pro, Ultra)", settings.deviceBadge) { settings.deviceBadge = it }
                        InlineField("Custom Motto / Subtitle", settings.customMotto) { settings.customMotto = it }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ToggleRow(
                            title = "Show EXIF Parameters",
                            subtitle = "Focal length, aperture, shutter speed, ISO",
                            checked = settings.showExifParams,
                            onCheckedChange = { settings.showExifParams = it },
                        )
                        ToggleRow(
                            title = "Show GPS & City on Frame",
                            subtitle = "Coordinates, altitude, and location name",
                            checked = settings.showGpsOnFrame,
                            onCheckedChange = { settings.showGpsOnFrame = it },
                        )
                        ToggleRow(
                            title = "Show Avatar / Logo",
                            subtitle = "Custom logo or photographer profile silhouette",
                            checked = settings.showAvatarOnFrame,
                            onCheckedChange = { settings.showAvatarOnFrame = it },
                        )
                    }
                }
            }

            // 3. Stamp Groups (GPS, Date/Time, ID, Weather, Sensors)
            StampGroup.entries.forEach { group ->
                val fields = StampField.entries.filter { it.group == group }
                item(key = "group_${group.name}") {
                    SettingsGroupCard(title = group.title) {
                        fields.forEachIndexed { index, field ->
                            if (index > 0) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                            }
                            FieldRow(settings, field, data, onToggle = { on -> toggle(field, on) })
                            when (field) {
                                StampField.MAP -> if (settings.isEnabled(field)) {
                                    ChoiceRow("Map Type", settings.mapType, MapType.entries) { settings.mapType = it }
                                    OfflineMapPackRow(settings, data)
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
                                        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        OutlinedButton(onClick = {
                                            logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                        }) { Text("Choose logo") }
                                        if (settings.hasCustomLogo) {
                                            TextButton(onClick = { settings.resetLogo() }) { Text("Use default") }
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
            }

            // 4. Custom Templates & Localization Card
            item(key = "stamp_settings") {
                SettingsGroupCard(title = "Stamp Settings & Localization") {
                    ChoiceRow("Preset Template", StampTemplate.STANDARD, StampTemplate.entries) { settings.applyTemplate(it) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(
                            "Save / Load Custom Templates",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                    colors = if (has) ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                                    else ButtonDefaults.outlinedButtonColors(),
                                ) {
                                    Text(if (has) "Load $slot" else "Save $slot", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ChoiceRow("Stamp Language", settings.language, StampLanguage.entries) { settings.language = it }
                    ChoiceRow("Font Style", settings.font, FontStyle.entries) { settings.font = it }
                    ChoiceRow("Stamp Color", settings.color, StampColor.entries) { settings.color = it }
                    ChoiceRow("Background", settings.background, StampBackground.entries) { settings.background = it }
                    ChoiceRow("Stamp Size", settings.size, StampSize.entries) { settings.size = it }
                    ChoiceRow("Stamp Position", settings.position, StampPosition.entries) { settings.position = it }
                    ChoiceRow("Map Position", settings.mapPosition, MapPosition.entries) { settings.mapPosition = it }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row {
                            Text("Map Scale", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                            Text("${settings.mapScalePercent}%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = settings.mapScalePercent.toFloat(),
                            onValueChange = { settings.mapScalePercent = it.toInt() },
                            valueRange = 40f..100f,
                        )
                    }

                    if (cam != null) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ToggleRow(
                            title = "Auto-Upload to Google Drive",
                            subtitle = "Prompts Google Drive upload after every capture",
                            checked = cam.autoDriveUpload,
                            onCheckedChange = { cam.autoDriveUpload = it },
                        )
                    }
                }
            }

            // Multi-Project Geofence Boundaries Card
            item(key = "geofence_sites_section") {
                SettingsGroupCard(title = "Multi-Project Geofence Boundaries") {
                    var showAddSiteDialog by remember { mutableStateOf(false) }

                    ToggleRow(
                        title = "Auto-switch project tags on site entry",
                        subtitle = "Automatically populates Project, Contractor & Client tags inside site radius",
                        checked = geoMgr.autoSwitchEnabled.value,
                        onCheckedChange = { geoMgr.setAutoSwitch(it) }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        if (geoMgr.sites.isEmpty()) {
                            Text(
                                "No project boundaries configured. Add work sites below to automatically set project, contractor, and client tags when entering the site radius.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            for (site in geoMgr.sites) {
                                val curLat = data.latitude
                                val curLon = data.longitude
                                val dist = if (curLat != null && curLon != null) {
                                    GeofenceSite.distanceMeters(curLat, curLon, site.latitude, site.longitude)
                                } else null
                                val isInside = dist != null && dist <= site.radiusMeters

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(site.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                            Spacer(Modifier.width(8.dp))
                                            if (isInside) {
                                                Surface(
                                                    color = Color(0xFFE8F5E9),
                                                    shape = RoundedCornerShape(4.dp),
                                                ) {
                                                    Text(
                                                        "ACTIVE (${dist?.toInt()}m)",
                                                        color = Color(0xFF2E7D32),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    )
                                                }
                                            }
                                        }
                                        val distText = if (dist != null) String.format(java.util.Locale.US, "%.1f km away", dist / 1000.0) else "Distance unknown"
                                        Text(
                                            "Radius: ${site.radiusMeters.toInt()}m • $distText",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (site.contractor.isNotBlank() || site.client.isNotBlank()) {
                                            Text(
                                                "Tags: ${listOfNotNull(site.contractor.takeIf { it.isNotBlank() }, site.client.takeIf { it.isNotBlank() }).joinToString(" • ")}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }

                                    IconButton(onClick = { geoMgr.removeSite(site.id) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete Site", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = { showAddSiteDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Icon(Icons.Filled.AddLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Add Work Site Geofence")
                        }
                    }

                    if (showAddSiteDialog) {
                        AddGeofenceSiteDialog(
                            currentLat = data.latitude,
                            currentLon = data.longitude,
                            onDismiss = { showAddSiteDialog = false },
                            onSave = { site ->
                                geoMgr.addSite(site)
                                showAddSiteDialog = false
                                Toast.makeText(context, "Added site: ${site.name}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }

            // External Bluetooth GNSS / RTK Receiver Card
            item(key = "bluetooth_rtk_section") {
                SettingsGroupCard(title = "External Bluetooth GNSS / RTK Receiver") {
                    BluetoothGpsSection(manager = btManager)
                }
            }

            // 5. Display Parameters Card
            if (cam != null) {
                item(key = "display_parameters_section") {
                    SettingsGroupCard(title = "Display Parameters") {
                        ToggleRow(
                            title = "Focus Peaking",
                            subtitle = "Highlight in-focus contrast edges with neon green lines in manual focus",
                            checked = cam.focusPeaking,
                            onCheckedChange = { cam.focusPeaking = it },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ToggleRow(
                            title = "Exposure Verification",
                            subtitle = "Zebra pattern: Blue for underexposed shadows & Red for overexposed highlights",
                            checked = cam.exposureVerification,
                            onCheckedChange = { cam.exposureVerification = it },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ToggleRow(
                            title = "Histogram",
                            subtitle = "Real-time luminance distribution graph on viewfinder",
                            checked = cam.histogram,
                            onCheckedChange = { cam.histogram = it },
                        )
                    }
                }

                // 6. Video Settings Card
                item(key = "video_settings_section") {
                    SettingsGroupCard(title = "Video Settings") {
                        ChoiceRow("Video Encoder", cam.videoCodec, VideoCodec.entries) { cam.videoCodec = it }
                        ChoiceRow("Take Photos While Recording", cam.snapshotMode, SnapshotMode.entries) { cam.snapshotMode = it }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ToggleRow(
                            title = "Auto Frame Rate",
                            subtitle = "Auto adjust 60 fps to 30/24 fps in low light or high heat",
                            checked = cam.autoFrameRate,
                            onCheckedChange = { cam.autoFrameRate = it },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ToggleRow(
                            title = "Allow Tagging Videos",
                            subtitle = "Show Tag button while recording to bookmark moments with JSON timestamps",
                            checked = cam.allowVideoTagging,
                            onCheckedChange = { cam.allowVideoTagging = it },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ToggleRow(
                            title = "Motion Tracking Focus",
                            subtitle = "Tap moving subject to continuously track and keep in focus",
                            checked = cam.motionTrackingFocus,
                            onCheckedChange = { cam.motionTrackingFocus = it },
                        )
                    }
                }

                // 7. Preserve Settings Card
                item(key = "preserve_settings_section") {
                    SettingsGroupCard(title = "Preserve Settings") {
                        Text(
                            text = "Retain previously used camera modes and parameters instead of automatically resetting to default choices when launching the camera.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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

                // 8. Hardware Buttons Card
                item(key = "hardware_buttons_section") {
                    SettingsGroupCard(title = "Hardware Buttons") {
                        ChoiceRow("Function of Volume Buttons", cam.volumeButtonAction, VolumeButtonAction.entries) {
                            cam.volumeButtonAction = it
                        }
                    }
                }
            }

            item(key = "attribution") {
                Text(
                    "Map tiles © Esri and © OpenStreetMap contributors. Weather by Open-Meteo.com.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SettingsGroupCard(title: String, content: @Composable () -> Unit) {
    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            content()
        }
    }
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
    ListItem(
        headlineContent = {
            Text(
                field.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    valueFor(field, settings, data),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Icon(
                    if (on) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = if (on) "Enabled" else "Disabled",
                    tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(24.dp),
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!on) },
    )
}

@Composable
private fun InlineField(label: String, value: String, keyboard: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        shape = MaterialTheme.shapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
fun <T : Labeled> ChoiceRow(title: String, value: T, options: List<T>, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    value.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { open = true },
    )
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title, style = MaterialTheme.typography.titleLarge) },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    options.forEach { opt ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable { onSelect(opt); open = false }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = opt == value, onClick = { onSelect(opt); open = false })
                            Text(
                                opt.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 8.dp),
                            )
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
    ListItem(
        headlineContent = {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = if (subtitle != null) {
            {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else null,
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                thumbContent = if (checked) {
                    { Icon(Icons.Filled.Check, null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
                } else null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
    )
}

@Composable
private fun OfflineMapPackRow(settings: StampSettings, data: com.geostamp.camera.data.StampData) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cacheInfo by remember { mutableStateOf(OfflineMapManager.getCacheInfo(context)) }
    var downloading by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f), MaterialTheme.shapes.small)
            .padding(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "Offline Map Cache",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    String.format(java.util.Locale.US, "%.1f MB (%d tiles stored)", cacheInfo.mb, cacheInfo.fileCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (cacheInfo.fileCount > 0 && !downloading) {
                TextButton(onClick = {
                    OfflineMapManager.clearCache(context)
                    cacheInfo = OfflineMapManager.getCacheInfo(context)
                    Toast.makeText(context, "Map cache cleared", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (downloading) {
            Text(progressText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            val lat = settings.siteLat ?: data.latitude
            val lon = settings.siteLon ?: data.longitude
            val targetName = settings.siteName.ifBlank { "Current Location" }

            Button(
                onClick = {
                    if (lat == null || lon == null) {
                        Toast.makeText(context, "Location not available yet", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    downloading = true
                    progressText = "Downloading map pack for $targetName…"
                    scope.launch(Dispatchers.IO) {
                        val res = OfflineMapManager.downloadAreaPack(
                            context = context,
                            centerLat = lat,
                            centerLon = lon,
                            radiusKm = 3.0,
                            mapType = settings.mapType,
                            onProgress = { cur, tot ->
                                progressText = "Downloading ($cur/$tot tiles)…"
                            },
                        )
                        downloading = false
                        cacheInfo = OfflineMapManager.getCacheInfo(context)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Cached ${res.downloadedTiles} new tiles for $targetName", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
            ) {
                Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Download Site Map Pack (3 km radius)")
            }
        }
    }
}
