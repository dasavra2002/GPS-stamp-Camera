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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.BrandingWatermark
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Circle
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
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.geostamp.camera.data.CameraSettings
import com.geostamp.camera.data.CoordinateConverter
import com.geostamp.camera.data.CoordinateFormat
import com.geostamp.camera.data.DateFormat
import com.geostamp.camera.data.FontStyle
import com.geostamp.camera.data.FrameDataDensity
import com.geostamp.camera.data.FrameGuide
import com.geostamp.camera.data.GeofenceManager
import com.geostamp.camera.data.GeofenceSite
import com.geostamp.camera.data.GridType
import com.geostamp.camera.data.Labeled
import com.geostamp.camera.data.MapPosition
import com.geostamp.camera.data.MapType
import com.geostamp.camera.data.OfflineMapManager
import com.geostamp.camera.data.TileSources
import com.geostamp.camera.data.PhotoAspect
import com.geostamp.camera.data.PhotoFormat
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
import com.geostamp.camera.data.VideoAspect
import com.geostamp.camera.data.VideoCodec
import com.geostamp.camera.data.VideoRes
import com.geostamp.camera.data.VolumeButtonAction
import com.geostamp.camera.data.WatermarkStyle
import com.geostamp.camera.location.BluetoothGpsManager
import com.geostamp.camera.stamp.FrameWatermarkRenderer
import com.geostamp.camera.stamp.PhotoComposer
import com.geostamp.camera.stamp.ShotParams
import com.geostamp.camera.stamp.WatermarkRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime

private data class SettingsTabItem(
    val title: String,
    val icon: ImageVector,
)

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
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val tabs = remember {
        listOf(
            SettingsTabItem("Watermarks", Icons.Filled.BrandingWatermark),
            SettingsTabItem("Camera", Icons.Filled.CameraAlt),
            SettingsTabItem("GPS & Sensors", Icons.Filled.LocationOn),
            SettingsTabItem("General", Icons.Filled.Settings),
        )
    }

    LaunchedEffect(data, config) {
        preview = withContext(Dispatchers.Default) {
            if (config.watermarkStyle != WatermarkStyle.CLASSIC_GEO_BADGE) {
                val dummy = Bitmap.createBitmap(1080, 810, Bitmap.Config.ARGB_8888).apply {
                    val canvas = android.graphics.Canvas(this)
                    val p = android.graphics.Paint().apply { color = android.graphics.Color.DKGRAY }
                    canvas.drawRect(0f, 0f, 1080f, 810f, p)
                }
                val sampleShot = ShotParams(focalMm = 26, aperture = 1.5f, shutterNs = 333333333L, iso = 12800, model = config.deviceBrand)
                val framed = FrameWatermarkRenderer.render(dummy, config, data, ZonedDateTime.now(), sampleShot, isPreview = false)
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
            else Toast.makeText(context, "Couldn't read image", Toast.LENGTH_SHORT).show()
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
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                TopAppBar(
                    title = {
                        Text(
                            "Settings",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
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
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    },
                ) {
                    tabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    tab.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                                )
                            },
                            icon = {
                                Icon(
                                    tab.icon,
                                    contentDescription = tab.title,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when (selectedTab) {
                0 -> WatermarksTabContent(
                    settings = settings,
                    data = data,
                    config = config,
                    preview = preview,
                    onToggleField = { field, on -> toggle(field, on) },
                    onPickLogo = {
                        logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                )
                1 -> CameraTabContent(cam = cam)
                2 -> GpsAndSensorsTabContent(
                    settings = settings,
                    data = data,
                    geoMgr = geoMgr,
                    btManager = btManager,
                    onToggleField = { field, on -> toggle(field, on) },
                )
                3 -> GeneralTabContent(
                    settings = settings,
                    cam = cam,
                )
            }
        }
    }
}

// ==========================================================================================
// Tab 0: Watermarks & Photo Frames
// ==========================================================================================

@Composable
private fun WatermarksTabContent(
    settings: StampSettings,
    data: com.geostamp.camera.data.StampData,
    config: com.geostamp.camera.data.StampConfig,
    preview: Bitmap?,
    onToggleField: (StampField, Boolean) -> Unit,
    onPickLogo: () -> Unit,
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1. Live Interactive Preview Card
        item(key = "live_preview_card") {
            ElevatedCard(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Text(
                                "Live preview",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Text(
                                settings.watermarkStyle.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
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
                            "Watermark is currently switched off",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
            }
        }

        // 2. Watermark Style & Frame Design
        item(key = "watermark_style_section") {
            SettingsGroupCard(title = "Watermark style") {
                ChoiceRow("Frame Design", settings.watermarkStyle, WatermarkStyle.entries) {
                    settings.watermarkStyle = it
                }
                if (settings.watermarkStyle != WatermarkStyle.CLASSIC_GEO_BADGE) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    ChoiceRow("Information Density", settings.frameDataDensity, FrameDataDensity.entries) {
                        settings.frameDataDensity = it
                    }
                    InlineField("Device Brand / Name", settings.deviceBrand) { settings.deviceBrand = it }
                    InlineField("Device Badge (e.g. Pro, Ultra)", settings.deviceBadge) { settings.deviceBadge = it }
                    InlineField("Custom Motto / Subtitle", settings.customMotto) { settings.customMotto = it }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
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

        // 3. Project Tags, Notes & Identity
        item(key = "identity_fields_section") {
            SettingsGroupCard(title = "Project & notes") {
                val idFields = StampField.entries.filter { it.group == StampGroup.BRANDING }
                idFields.forEachIndexed { index, field ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                    FieldRow(settings, field, data, onToggle = { on -> onToggleField(field, on) })
                    when (field) {
                        StampField.LOGO -> if (settings.isEnabled(field)) {
                            Row(
                                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(onClick = onPickLogo) { Text("Choose Custom Logo") }
                                if (settings.hasCustomLogo) {
                                    TextButton(onClick = { settings.resetLogo() }) { Text("Use Default") }
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
                        StampField.NUMBERING -> if (settings.isEnabled(field)) {
                            InlineField("Next photo number", settings.nextNumber.toString(), KeyboardType.Number) { v ->
                                v.filter { it.isDigit() }.take(9).toIntOrNull()?.let { settings.nextNumber = it }
                            }
                        }
                        else -> Unit
                    }
                }
            }
        }

        // 4. Custom Templates & Localization
        item(key = "templates_and_localization_section") {
            SettingsGroupCard(title = "Appearance & presets") {
                ChoiceRow("Preset Template", StampTemplate.STANDARD, StampTemplate.entries) { settings.applyTemplate(it) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(
                        "Save / Load Custom Presets",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        (1..3).forEach { slot ->
                            val has = settings.hasCustomTemplate(slot)
                            OutlinedButton(
                                onClick = {
                                    if (has) {
                                        settings.loadCustomTemplate(slot)
                                        Toast.makeText(context, "Loaded Template $slot", Toast.LENGTH_SHORT).show()
                                    } else {
                                        settings.saveCustomTemplate(slot, "Template $slot")
                                        Toast.makeText(context, "Saved setup to Template $slot", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = if (has) ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                                else ButtonDefaults.outlinedButtonColors(),
                            ) {
                                Text(if (has) "Slot $slot" else "Save $slot", fontSize = 12.sp)
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ChoiceRow("Stamp Language", settings.language, StampLanguage.entries) { settings.language = it }
                ChoiceRow("Font Family", settings.font, FontStyle.entries) { settings.font = it }
                ChoiceRow("Text Color", settings.color, StampColor.entries) { settings.color = it }
                ChoiceRow("Background Theme", settings.background, StampBackground.entries) { settings.background = it }
                ChoiceRow("Badge Size", settings.size, StampSize.entries) { settings.size = it }
                ChoiceRow("Badge Position", settings.position, StampPosition.entries) { settings.position = it }
                ChoiceRow("Mini-Map Placement", settings.mapPosition, MapPosition.entries) { settings.mapPosition = it }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Mini-Map Scale",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${settings.mapScalePercent}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Slider(
                        value = settings.mapScalePercent.toFloat(),
                        onValueChange = { settings.mapScalePercent = it.toInt() },
                        valueRange = 40f..100f,
                    )
                }
            }
        }
    }
}

// ==========================================================================================
// Tab 1: Camera & Optics
// ==========================================================================================

@Composable
private fun CameraTabContent(cam: CameraSettings?) {
    if (cam == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Camera hardware controls unavailable", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Photo Specs Card
        item(key = "photo_specs_card") {
            SettingsGroupCard(title = "Photo") {
                ChoiceRow("Photo Format", cam.photoFormat, PhotoFormat.entries) { cam.photoFormat = it }
                ChoiceRow("Photo Aspect Ratio", cam.photoAspect, PhotoAspect.entries) { cam.photoAspect = it }
                ChoiceRow("Timer Delay", cam.timerSeconds, listOf(0, 3, 5, 10)) { cam.timerSeconds = it }
                ChoiceRow("Burst Multiplier", cam.burstCount, listOf(1, 3, 5, 10)) { cam.burstCount = it }
            }
        }

        // Viewfinder Guides & Assists
        item(key = "viewfinder_assists_card") {
            SettingsGroupCard(title = "Viewfinder guides") {
                ChoiceRow("Grid Guides", cam.grid, GridType.entries) { cam.grid = it }
                ChoiceRow("Frame Aspect Mask", cam.frameGuide, FrameGuide.entries) { cam.frameGuide = it }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ToggleRow(
                    title = "Focus Peaking",
                    subtitle = "Highlight in-focus contrast edges with neon green lines in manual focus",
                    checked = cam.focusPeaking,
                    onCheckedChange = { cam.focusPeaking = it },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ToggleRow(
                    title = "Exposure Verification",
                    subtitle = "Zebra pattern: Blue for underexposed shadows & Red for overexposed highlights",
                    checked = cam.exposureVerification,
                    onCheckedChange = { cam.exposureVerification = it },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ToggleRow(
                    title = "Viewfinder Histogram",
                    subtitle = "Real-time luminance distribution graph on viewfinder",
                    checked = cam.histogram,
                    onCheckedChange = { cam.histogram = it },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ToggleRow(
                    title = "Electronic Horizon Level",
                    subtitle = "Show pitch and roll tilt indicator on preview",
                    checked = cam.level,
                    onCheckedChange = { cam.level = it },
                )
            }
        }

        // Video Settings Card
        item(key = "video_settings_card") {
            SettingsGroupCard(title = "Video") {
                ChoiceRow("Video Resolution", cam.videoRes, VideoRes.entries) { cam.videoRes = it }
                ChoiceRow("Video Aspect Ratio", cam.videoAspect, VideoAspect.entries) { cam.videoAspect = it }
                ChoiceRow("Video Encoder", cam.videoCodec, VideoCodec.entries) { cam.videoCodec = it }
                ChoiceRow("Take Photos While Recording", cam.snapshotMode, SnapshotMode.entries) { cam.snapshotMode = it }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ToggleRow(
                    title = "Auto Frame Rate",
                    subtitle = "Auto adjust 60 fps to 30/24 fps in low light or high heat",
                    checked = cam.autoFrameRate,
                    onCheckedChange = { cam.autoFrameRate = it },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ToggleRow(
                    title = "Allow Tagging Videos",
                    subtitle = "Show Tag button while recording to bookmark moments with JSON timestamps",
                    checked = cam.allowVideoTagging,
                    onCheckedChange = { cam.allowVideoTagging = it },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ToggleRow(
                    title = "Motion Tracking Focus",
                    subtitle = "Tap moving subject to continuously track and keep in focus",
                    checked = cam.motionTrackingFocus,
                    onCheckedChange = { cam.motionTrackingFocus = it },
                )
            }
        }

        // Hardware Buttons Card
        item(key = "hardware_buttons_card") {
            SettingsGroupCard(title = "Volume buttons") {
                ChoiceRow("Volume Buttons Action", cam.volumeButtonAction, VolumeButtonAction.entries) {
                    cam.volumeButtonAction = it
                }
            }
        }

        // Preserve Settings Card
        item(key = "preserve_settings_card") {
            SettingsGroupCard(title = "Remember between launches") {
                Text(
                    text = "Retain previously used modes and parameters instead of resetting to default choices when launching the camera.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ToggleRow(
                    title = "Camera Mode",
                    subtitle = "Preserve Photo / Video / Timelapse choice",
                    checked = cam.preserveCameraMode,
                    onCheckedChange = { cam.preserveCameraMode = it },
                )
                ToggleRow(
                    title = "Selected Lens",
                    subtitle = "Preserve focal length and active lens",
                    checked = cam.preservePortraitFocalLength,
                    onCheckedChange = { cam.preservePortraitFocalLength = it },
                )
                ToggleRow(
                    title = "Manual Parameters",
                    subtitle = "Preserve manual ISO, shutter speed, WB Kelvin, and focus",
                    checked = cam.preserveParameters,
                    onCheckedChange = { cam.preserveParameters = it },
                )
                ToggleRow(
                    title = "Exposure Compensation",
                    subtitle = "Preserve custom EV brightness offsets",
                    checked = cam.preserveExposureCompensation,
                    onCheckedChange = { cam.preserveExposureCompensation = it },
                )
                ToggleRow(
                    title = "Night Mode Extension",
                    subtitle = "Preserve low-light night mode toggle",
                    checked = cam.preserveAutoNightMode,
                    onCheckedChange = { cam.preserveAutoNightMode = it },
                )
                ToggleRow(
                    title = "Document & AI Mode",
                    subtitle = "Preserve document scanner and signboard OCR mode",
                    checked = cam.preserveAiCamera,
                    onCheckedChange = { cam.preserveAiCamera = it },
                )
            }
        }
    }
}

// ==========================================================================================
// Tab 2: GPS, Geofencing & Sensors
// ==========================================================================================

@Composable
private fun GpsAndSensorsTabContent(
    settings: StampSettings,
    data: com.geostamp.camera.data.StampData,
    geoMgr: GeofenceManager,
    btManager: BluetoothGpsManager,
    onToggleField: (StampField, Boolean) -> Unit,
) {
    val context = LocalContext.current
    var showAddSiteDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Coordinates and Position Card
        item(key = "coordinates_card") {
            SettingsGroupCard(title = "Coordinates") {
                ChoiceRow("Coordinate Format", settings.coordinateFormat, CoordinateFormat.entries) {
                    settings.coordinateFormat = it
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                val gpsFields = StampField.entries.filter { it.group == StampGroup.LOCATION }
                gpsFields.forEachIndexed { index, field ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                    FieldRow(settings, field, data, onToggle = { on -> onToggleField(field, on) })
                    if (field == StampField.MAP && settings.isEnabled(field)) {
                        ChoiceRow("Map Type", settings.mapType, MapType.entries) { settings.mapType = it }
                    }
                }
            }
        }

        // Multi-Project Geofence Boundaries Card
        item(key = "geofence_card") {
            SettingsGroupCard(title = "Geofences") {
                ToggleRow(
                    title = "Auto-switch project tags on site entry",
                    subtitle = "Automatically populates Project, Contractor & Client tags inside site radius",
                    checked = geoMgr.autoSwitchEnabled.value,
                    onCheckedChange = { geoMgr.setAutoSwitch(it) },
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

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
                                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                                shape = CircleShape,
                                            ) {
                                                Text(
                                                    "Active · ${dist?.toInt()} m",
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
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
                        shape = RoundedCornerShape(12.dp),
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
                        },
                    )
                }
            }
        }

        // Bluetooth GNSS / RTK Receiver Card
        item(key = "bluetooth_rtk_card") {
            SettingsGroupCard(title = "Bluetooth GNSS / RTK receiver") {
                BluetoothGpsSection(manager = btManager)
            }
        }

        // Weather & Environment Sensors Card
        item(key = "weather_sensors_card") {
            SettingsGroupCard(title = "Weather") {
                val weatherFields = StampField.entries.filter { it.group == StampGroup.WEATHER }
                weatherFields.forEachIndexed { index, field ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                    FieldRow(settings, field, data, onToggle = { on -> onToggleField(field, on) })
                }
            }
        }

        // Device Sensors (Compass, Magnetic, Sound) Card
        item(key = "device_sensors_card") {
            SettingsGroupCard(title = "Compass & sound") {
                val sensorFields = StampField.entries.filter { it.group == StampGroup.SENSORS }
                sensorFields.forEachIndexed { index, field ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                    FieldRow(settings, field, data, onToggle = { on -> onToggleField(field, on) })
                }
            }
        }

        // Offline Map Pack Card
        item(key = "offline_map_cache_card") {
            SettingsGroupCard(title = "Maps & offline storage") {
                InlineField("Custom Tile Server URL ({z}/{x}/{y})", settings.customTileUrl, KeyboardType.Uri) { settings.customTileUrl = it.trim() }
                if (settings.customTileUrl.isNotBlank()) {
                    InlineField("Map Attribution (shown on stamp)", settings.customTileAttribution) { settings.customTileAttribution = it }
                    if (!TileSources.isValidTemplate(settings.customTileUrl)) {
                        Text(
                            "Needs an https:// URL containing {z}, {x} and {y}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
                OfflineMapPackRow(settings, data)
            }
        }
    }
}

// ==========================================================================================
// Tab 3: General & App Info
// ==========================================================================================

@Composable
private fun GeneralTabContent(
    settings: StampSettings,
    cam: CameraSettings?,
) {
    val context = LocalContext.current
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Date & Numbering Card
        item(key = "date_numbering_card") {
            SettingsGroupCard(title = "Date, time & numbering") {
                ChoiceRow("Date Format", settings.dateFormat, DateFormat.entries) { settings.dateFormat = it }
                InlineField("Next Photo Sequence Number", settings.nextNumber.toString(), KeyboardType.Number) { v ->
                    v.filter { it.isDigit() }.take(9).toIntOrNull()?.let { settings.nextNumber = it }
                }
            }
        }

        // Cloud & Automation Card
        if (cam != null) {
            item(key = "cloud_automation_card") {
                SettingsGroupCard(title = "Cloud upload") {
                    ToggleRow(
                        title = "Keep Unstamped Original",
                        subtitle = "Also saves the camera's clean photo, signed, to Pictures/GeoStamp/Originals",
                        checked = cam.keepOriginal,
                        onCheckedChange = { cam.keepOriginal = it },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    ToggleRow(
                        title = "Auto-Upload to Google Drive",
                        subtitle = "Prompts Google Drive upload after every capture",
                        checked = cam.autoDriveUpload,
                        onCheckedChange = { cam.autoDriveUpload = it },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    ToggleRow(
                        title = "Attendance Punch Mode",
                        subtitle = "Switches to selfie lens and attendance verification stamp template",
                        checked = cam.attendanceMode,
                        onCheckedChange = { cam.attendanceMode = it },
                    )
                }
            }
        }

        // Attribution & Version Card
        item(key = "about_attribution_card") {
            SettingsGroupCard(title = "About & map attribution") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        if (versionName != null) "GeoStamp Pro Camera v$versionName" else "GeoStamp Pro Camera",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Map tiles © Esri and © OpenStreetMap contributors.\nReal-time weather data provided by Open-Meteo.com.\nHigh-precision geocoding and reverse location services.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp,
                    )
                }
            }
        }
    }
}

// ==========================================================================================
// Reusable Material 3 UI Components
// ==========================================================================================

@Composable
private fun SettingsGroupCard(title: String, content: @Composable () -> Unit) {
    SectionCard(title = title, contentPadding = PaddingValues(vertical = 4.dp), spacing = 0.dp) { content() }
}

/** Current value shown on the right of each row. */
private fun valueFor(field: StampField, settings: StampSettings, d: com.geostamp.camera.data.StampData): String {
    fun f(v: Double?, fmt: String) = v?.let { String.format(java.util.Locale.US, fmt, it) } ?: "-"
    return when (field) {
        StampField.MAP -> settings.mapType.label
        StampField.TITLE -> d.title ?: "-"
        StampField.COUNTRY_FLAG -> WatermarkRenderer.flagEmoji(d.countryCode)
        StampField.SHORT_ADDRESS -> d.shortAddress ?: "-"
        StampField.ADDRESS -> d.address ?: "-"
        StampField.LAT_LONG -> if (d.latitude != null && d.longitude != null) CoordinateConverter.format(d.latitude, d.longitude, settings.coordinateFormat) else "-"
        StampField.PLUS_CODE -> d.plusCode ?: "-"
        StampField.ALTITUDE -> f(d.altitude, "%.0f m")
        StampField.ACCURACY -> d.accuracyM?.let { String.format(java.util.Locale.US, "%.0f m", it) } ?: "-"
        StampField.DATE_TIME -> settings.dateFormat.label
        StampField.TIME_ZONE -> "GMT " + java.time.format.DateTimeFormatter.ofPattern("xxx").format(ZonedDateTime.now())
        StampField.NUMBERING -> settings.nextNumber.toString()
        StampField.LOGO -> if (settings.hasCustomLogo) "Custom" else "Default"
        StampField.QR_CODE -> "Location QR Code"
        StampField.PROJECT -> settings.siteName.ifBlank { "-" }
        StampField.NOTE -> settings.note.ifBlank { "-" }
        StampField.PERSON_NAME -> settings.personName.ifBlank { "-" }
        StampField.PHONE -> settings.phone.ifBlank { "-" }
        StampField.CUSTOM_1 -> "${settings.custom1Label}: ${settings.custom1Value.ifBlank { "-" }}"
        StampField.CUSTOM_2 -> "${settings.custom2Label}: ${settings.custom2Value.ifBlank { "-" }}"
        StampField.TEMPERATURE -> f(d.temperatureC, "%.1f°C")
        StampField.WIND -> f(d.windKmh, "%.1f km/h")
        StampField.HUMIDITY -> d.humidity?.let { "$it%" } ?: "-"
        StampField.PRESSURE -> f(d.pressureHpa, "%.0f hPa")
        StampField.AQI -> d.aqi?.toString() ?: "-"
        StampField.SUN_TIMES -> if (d.sunrise != null && d.sunset != null) "${d.sunrise} / ${d.sunset}" else "-"
        StampField.COMPASS -> d.azimuthDeg?.let { String.format(java.util.Locale.US, "%.0f° %s", it, WatermarkRenderer.direction(it)) } ?: "-"
        StampField.MAGNETIC -> d.magneticUt?.let { String.format(java.util.Locale.US, "%.1f µT", it) } ?: "-"
        StampField.SOUND_LEVEL -> d.soundDb?.let { String.format(java.util.Locale.US, "%.0f dB", it) } ?: "-"
        StampField.CAMERA_INFO -> "Lens Info"
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
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 140.dp),
                )
                Switch(checked = on, onCheckedChange = onToggle)
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
        shape = RoundedCornerShape(12.dp),
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
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 160.dp),
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
            title = { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    options.forEach { opt ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelect(opt); open = false }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = opt == value,
                                onClick = { onSelect(opt); open = false },
                            )
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
fun ChoiceRow(title: String, value: Int, options: List<Int>, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val displayValue = if (value == 0) "Off" else "${value}s"
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
                    displayValue,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
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
            title = { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    options.forEach { opt ->
                        val optLabel = if (opt == 0) "Off" else "${opt}s"
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelect(opt); open = false }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = opt == value, onClick = { onSelect(opt); open = false })
                            Text(
                                optLabel,
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
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
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
        } else if (!TileSources.forType(settings.mapType).bulkDownloadAllowed) {
            Text(
                "Map packs can't be downloaded from the free OpenStreetMap and Esri servers: their terms forbid " +
                    "bulk downloads. Maps you have already seen stay cached. To download whole sites for offline " +
                    "work, add a custom tile server above from a provider whose plan allows offline use " +
                    "(for example MapTiler, Thunderforest or Stadia Maps).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                            source = TileSources.forType(settings.mapType),
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
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Download Site Map Pack (3 km radius)")
            }
        }
    }
}
