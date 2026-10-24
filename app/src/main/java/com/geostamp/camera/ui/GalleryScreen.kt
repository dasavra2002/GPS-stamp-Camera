package com.geostamp.camera.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Explore
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Card
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.geostamp.camera.data.StampData
import com.geostamp.camera.data.StampDataRepository
import com.geostamp.camera.data.StampSettings
import com.geostamp.camera.report.ReportGenerator
import com.geostamp.camera.stamp.CameraExif
import com.geostamp.camera.stamp.GalleryStamper
import com.geostamp.camera.stamp.PhotoComposer
import com.geostamp.camera.stamp.PhotoMeta
import com.geostamp.camera.stamp.PhotoSaver
import com.geostamp.camera.stamp.PlusCode
import com.geostamp.camera.stamp.WatermarkRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class ItemStatus(val text: String) {
    READING("Reading metadata…"),
    LOOKING_UP("Looking up address, map & weather…"),
    READY("Ready to stamp"),
    NEEDS_INFO("No location in photo – tap Edit details"),
    SAVING("Saving…"),
    SAVED("Saved to Pictures/GeoStamp"),
    FAILED("Failed"),
}

private enum class GalleryViewMode { LIST, MAP }

/** One gallery photo being stamped. Its [data] and [time] can be edited by the user. */
class GalleryItem(val uri: Uri, val name: String, val thumb: Bitmap?, val meta: PhotoMeta) {
    var data by mutableStateOf(StampData())
    var time by mutableStateOf(meta.time ?: ZonedDateTime.now())
    var status by mutableStateOf(ItemStatus.READING)
    var savedUri by mutableStateOf<Uri?>(null)
    var edited by mutableStateOf(false)
    var audioMemoPath by mutableStateOf<String?>(null)
}

/** Survives switching between the camera and gallery screens. */
class GalleryState(val scope: CoroutineScope) {
    val items = mutableStateListOf<GalleryItem>()
    var editing by mutableStateOf<GalleryItem?>(null)
    var busy by mutableStateOf(false)
}

private fun mediaPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        Manifest.permission.ACCESS_MEDIA_LOCATION,
    )
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.ACCESS_MEDIA_LOCATION,
    )
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.ACCESS_MEDIA_LOCATION,
    )
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    state: GalleryState,
    settings: StampSettings,
    repo: StampDataRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var viewMode by remember { mutableStateOf(GalleryViewMode.LIST) }
    var showSignatureDialog by remember { mutableStateOf(false) }
    var showVerify by remember { mutableStateOf(false) }

    fun shareItemWhatsApp(item: GalleryItem) {
        val uri = item.savedUri ?: item.uri
        val text = buildString {
            append("📍 GeoStamp Photo\n")
            item.data.title?.let { append("🏢 Location: $it\n") }
            if (item.data.latitude != null && item.data.longitude != null) {
                append("🗺 GPS: ${item.data.latitude}, ${item.data.longitude}\n")
            }
            item.data.plusCode?.let { append("🌐 Plus Code: $it\n") }
            append("⏰ Time: ${WatermarkRenderer.formatDate(settings.snapshot(), item.time)}\n")
            if (settings.note.isNotBlank()) append("📝 Note: ${settings.note}\n")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            val fallback = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(fallback, "Share via"))
        }
    }

    fun openInGoogleMaps(lat: Double, lon: Double, label: String) {
        val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(label)})")
        val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        }
        try {
            context.startActivity(mapIntent)
        } catch (_: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=$lat,$lon"))
            context.startActivity(webIntent)
        }
    }

    // Editing screen takes over when an item is selected.
    state.editing?.let { item ->
        EditDetailsScreen(
            item = item,
            settings = settings,
            repo = repo,
            onDone = { state.editing = null },
        )
        return
    }
    BackHandler(onBack = onBack)

    fun loadPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        state.scope.launch {
            state.busy = true
            val mapType = settings.mapType
            val newItems = withContext(Dispatchers.IO) {
                uris.map { picked ->
                    val uri = GalleryStamper.originalUri(context, picked)
                    val meta = GalleryStamper.readMeta(context, uri)
                    GalleryItem(uri, GalleryStamper.displayName(context, picked), PhotoComposer.thumbnail(context, uri), meta).also {
                        it.data = StampData(
                            latitude = meta.latitude, longitude = meta.longitude, altitude = meta.altitude,
                            plusCode = if (meta.hasLocation) PlusCode.encode(meta.latitude!!, meta.longitude!!) else null,
                            azimuthDeg = meta.direction,
                        )
                        it.status = if (meta.hasLocation) ItemStatus.LOOKING_UP else ItemStatus.NEEDS_INFO
                    }
                }
            }
            state.items.addAll(newItems)
            state.busy = false
            for (item in newItems.filter { it.meta.hasLocation }) {
                val d = withContext(Dispatchers.IO) {
                    GalleryStamper.lookup(context, item.meta.latitude!!, item.meta.longitude!!, item.time, mapType, item.data)
                }
                if (!item.edited) item.data = d
                item.status = ItemStatus.READY
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        loadPhotos(uris)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.ACCESS_MEDIA_LOCATION] == false) {
            Toast.makeText(context, "Without media location access, GPS can't be read from photos – you can enter it by hand.", Toast.LENGTH_LONG).show()
        }
        picker.launch(arrayOf("image/*"))
    }

    fun pick() {
        val missing = mediaPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) picker.launch(arrayOf("image/*")) else permissionLauncher.launch(missing.toTypedArray())
    }

    suspend fun stamp(item: GalleryItem) {
        item.status = ItemStatus.SAVING
        val cfg = settings.snapshot()
        val uri = withContext(Dispatchers.Default) {
            try {
                val bmp = PhotoComposer.decodeUri(context, item.uri)
                val stamped = PhotoComposer.compose(bmp, cfg, item.data, item.time, item.meta.camera, item.meta.shotParams)
                val base = item.name.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9_-]"), "_").take(40)
                val source = when {
                    item.data.latitude == null -> "none"
                    item.edited -> "gallery-manual"
                    else -> "gallery-exif"
                }
                val exif = context.contentResolver.openInputStream(item.uri)?.use { CameraExif.read(it) }.orEmpty()
                PhotoSaver.save(
                    context, stamped, item.data, item.time, cfg.number, source,
                    prefix = "Stamped_$base", subFolder = cfg.siteName.ifBlank { null }, cameraExif = exif,
                ).also {
                    stamped.recycle()
                    if (bmp !== stamped && !bmp.isRecycled) bmp.recycle()
                }
            } catch (e: Throwable) {
                null
            }
        }
        if (uri != null) {
            item.savedUri = uri
            item.status = ItemStatus.SAVED
            settings.nextNumber = settings.nextNumber + 1
        } else {
            item.status = ItemStatus.FAILED
        }
    }

    fun useCurrentLocationForMissing() {
        val loc = repo.lastLocation
        if (loc == null) {
            Toast.makeText(context, "Current location isn't known yet", Toast.LENGTH_SHORT).show()
            return
        }
        val targets = state.items.filter { it.data.latitude == null }
        if (targets.isEmpty()) return
        state.scope.launch {
            for (item in targets) {
                item.status = ItemStatus.LOOKING_UP
                item.edited = true
                item.data = withContext(Dispatchers.IO) {
                    GalleryStamper.lookup(context, loc.latitude, loc.longitude, item.time, settings.mapType, item.data)
                }
                item.status = ItemStatus.READY
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Stamp from gallery",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { showVerify = true }) {
                        Icon(Icons.Filled.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Verify")
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // Header description
            Text(
                "Location and time come from each photo's EXIF; address, map and weather are looked up for you. Originals are never changed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            // Primary actions row (Pick Photos & Stamp All)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = { pick() },
                    enabled = !state.busy,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Pick photos", style = MaterialTheme.typography.labelLarge)
                }
                val stampable = state.items.filter { it.status == ItemStatus.READY }
                FilledTonalButton(
                    onClick = { state.scope.launch { stampable.forEach { stamp(it) } } },
                    enabled = stampable.isNotEmpty(),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Stamp all (${stampable.size})", style = MaterialTheme.typography.labelLarge)
                }
            }

            if (state.items.any { it.data.latitude == null }) {
                TextButton(
                    onClick = { useCurrentLocationForMissing() },
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Use current GPS for photos without location", style = MaterialTheme.typography.labelMedium)
                }
            }

            // Material 3 Action Chips Row for Reports & Backups
            if (state.items.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AssistChip(
                        onClick = { showSignatureDialog = true },
                        label = { Text("PDF report") },
                        leadingIcon = { Icon(Icons.Filled.Description, null, modifier = Modifier.size(16.dp)) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    )

                    AssistChip(
                        onClick = {
                            state.scope.launch {
                                val uri = ReportGenerator.generateCsvSummary(context, state.items)
                                if (uri != null) {
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/csv"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share CSV Summary"))
                                } else {
                                    Toast.makeText(context, "Could not generate CSV", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        label = { Text("CSV") },
                        leadingIcon = { Icon(Icons.Filled.TableChart, null, modifier = Modifier.size(16.dp)) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    )

                    AssistChip(
                        onClick = {
                            state.scope.launch {
                                val uri = ReportGenerator.generateKmlExport(context, state.items, docName = settings.note.ifBlank { "GeoStamp Map" })
                                if (uri != null) {
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/vnd.google-earth.kml+xml"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Open in Google Earth / Share KML"))
                                } else {
                                    Toast.makeText(context, "No photos with GPS to map", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        label = { Text("Google Earth") },
                        leadingIcon = { Icon(Icons.Filled.Public, null, modifier = Modifier.size(16.dp)) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    )

                    AssistChip(
                        onClick = {
                            val savedUris = state.items.mapNotNull { it.savedUri ?: it.uri }
                            if (savedUris.isEmpty()) {
                                Toast.makeText(context, "No photos to upload", Toast.LENGTH_SHORT).show()
                            } else if (savedUris.size == 1) {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/jpeg"
                                    putExtra(Intent.EXTRA_STREAM, savedUris.first())
                                    setPackage("com.google.android.apps.docs")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    val fallback = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/jpeg"
                                        putExtra(Intent.EXTRA_STREAM, savedUris.first())
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(fallback, "Upload to Google Drive / Cloud"))
                                }
                            } else {
                                val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                    type = "image/jpeg"
                                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(savedUris))
                                    setPackage("com.google.android.apps.docs")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    val fallback = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                        type = "image/jpeg"
                                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(savedUris))
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(fallback, "Upload photos to Cloud / Drive"))
                                }
                            }
                        },
                        label = { Text("Drive backup") },
                        leadingIcon = { Icon(Icons.Filled.CloudUpload, null, modifier = Modifier.size(16.dp)) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    )
                }

                // Material 3 Single Choice Segmented Button for View Mode
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    SegmentedButton(
                        selected = viewMode == GalleryViewMode.LIST,
                        onClick = { viewMode = GalleryViewMode.LIST },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) {
                        Text("List (${state.items.size})")
                    }
                    SegmentedButton(
                        selected = viewMode == GalleryViewMode.MAP,
                        onClick = { viewMode = GalleryViewMode.MAP },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) {
                        Text("Map (${state.items.count { it.data.latitude != null }})")
                    }
                }
            }

            if (state.busy) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Reading photos…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            if (state.items.isEmpty() && !state.busy) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Filled.PhotoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(56.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("No photos yet", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Pick photos to add a location stamp to them.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (viewMode == GalleryViewMode.LIST) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.items, key = { it.uri.toString() + it.hashCode() }) { item ->
                        ItemCard(
                            item = item,
                            onEdit = { state.editing = item },
                            onPreview = {
                                scope.launch {
                                    val cfg = settings.snapshot()
                                    previewBitmap = withContext(Dispatchers.Default) {
                                        runCatching {
                                            val full = PhotoComposer.decodeUri(context, item.uri)
                                            val w = minOf(1200, full.width)
                                            val small = Bitmap.createScaledBitmap(full, w, (full.height.toLong() * w / full.width).toInt().coerceAtLeast(1), true)
                                            if (small !== full) full.recycle()
                                            PhotoComposer.compose(small, cfg, item.data, item.time, item.meta.camera, item.meta.shotParams)
                                        }.getOrNull()
                                    }
                                }
                            },
                            onStamp = { state.scope.launch { stamp(item) } },
                            onOpen = {
                                item.savedUri?.let { uri ->
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "image/jpeg")
                                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                        )
                                    }
                                }
                            },
                            onRemove = { state.items.remove(item) },
                        )
                    }
                }
            } else {
                PhotoMapView(
                    items = state.items,
                    onOpenInMap = { lat, lon, label -> openInGoogleMaps(lat, lon, label) },
                    onStamp = { item -> state.scope.launch { stamp(item) } },
                    onShareWhatsApp = { item -> shareItemWhatsApp(item) },
                )
            }
        }
    }

    previewBitmap?.let { bmp ->
        AlertDialog(
            onDismissRequest = { previewBitmap = null },
            confirmButton = { TextButton(onClick = { previewBitmap = null }) { Text("Close") } },
            text = {
                Image(
                    bmp.asImageBitmap(),
                    "Stamped preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium),
                )
            },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }

    if (showVerify) {
        VerifyPhotoDialog(onDismiss = { showVerify = false })
    }

    if (showSignatureDialog) {
        DigitalSignatureDialog(
            initialInspectorName = settings.personName,
            initialCompanyName = settings.custom1Value.ifBlank { "Certified Field Inspection" },
            onDismiss = { showSignatureDialog = false },
            onConfirm = { signoff ->
                showSignatureDialog = false
                state.scope.launch {
                    Toast.makeText(context, "Generating signed inspection PDF report…", Toast.LENGTH_SHORT).show()
                    val uri = ReportGenerator.generatePdfReport(
                        context, state.items,
                        projectName = settings.siteName.ifBlank { settings.note.ifBlank { "Site Visit" } },
                        inspectorName = signoff.inspectorName,
                        inspectorRole = signoff.inspectorRole,
                        inspectorSignature = signoff.inspectorSignature,
                        clientName = signoff.clientName,
                        clientCompany = signoff.clientCompany,
                        clientSignature = signoff.clientSignature,
                        companyName = settings.custom1Value.ifBlank { "Certified Field Inspection" },
                        logoBitmap = settings.logo,
                        approvalStatus = signoff.approvalStatus,
                    )
                    if (uri != null) {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share Signed Inspection PDF"))
                    } else {
                        Toast.makeText(context, "Could not generate PDF", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onSkipSignatures = {
                showSignatureDialog = false
                state.scope.launch {
                    Toast.makeText(context, "Generating inspection PDF report…", Toast.LENGTH_SHORT).show()
                    val uri = ReportGenerator.generatePdfReport(
                        context, state.items,
                        projectName = settings.siteName.ifBlank { settings.note.ifBlank { "Site Visit" } },
                        inspectorName = settings.personName,
                        companyName = settings.custom1Value.ifBlank { "Certified Field Inspection" },
                        logoBitmap = settings.logo,
                        approvalStatus = "VERIFIED & APPROVED",
                    )
                    if (uri != null) {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share Inspection PDF"))
                    } else {
                        Toast.makeText(context, "Could not generate PDF", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }
}

@Composable
private fun ItemCard(
    item: GalleryItem,
    onEdit: () -> Unit,
    onPreview: () -> Unit,
    onStamp: () -> Unit,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 4.dp)) {
            Row {
                Box(
                    Modifier
                        .size(88.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    item.thumb?.let {
                        Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    val d = item.data
                    InfoLine(
                        icon = if (d.latitude != null) Icons.Filled.LocationOn else Icons.Filled.LocationOff,
                        text = if (d.latitude != null) String.format(Locale.US, "%.5f, %.5f", d.latitude, d.longitude) +
                            (if (item.meta.hasLocation && !item.edited) " · from photo" else " · entered")
                        else "No location",
                        color = if (d.latitude != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    )
                    InfoLine(
                        icon = Icons.Filled.Schedule,
                        text = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(item.time) +
                            when {
                                item.edited -> ""
                                item.meta.timeFromExif -> " · from photo"
                                item.meta.time != null -> " · file date"
                                else -> " · unknown, set it"
                            },
                    )
                    d.title?.let { InfoLine(icon = Icons.Filled.Business, text = it) }
                    if (item.audioMemoPath != null) {
                        InfoLine(icon = Icons.Filled.Mic, text = "Voice memo attached", color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(6.dp))
                    StatusPill(item.status)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onEdit, enabled = item.status != ItemStatus.SAVING) {
                    Text("Edit", style = MaterialTheme.typography.labelLarge)
                }
                TextButton(onClick = onPreview) {
                    Text("Preview", style = MaterialTheme.typography.labelLarge)
                }
                if (item.status == ItemStatus.SAVED) {
                    FilledTonalButton(onClick = onOpen) {
                        Text("Open", style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    Button(
                        onClick = onStamp,
                        enabled = item.status != ItemStatus.SAVING && item.status != ItemStatus.LOOKING_UP && item.status != ItemStatus.READING,
                    ) {
                        Text("Stamp", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoLine(icon: ImageVector, text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StatusPill(status: ItemStatus) {
    val (container, content) = when (status) {
        ItemStatus.FAILED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        ItemStatus.SAVED -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(shape = CircleShape, color = container) {
        Text(
            status.text,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

private fun Double?.str(fmt: String = "%s"): String = this?.let { String.format(Locale.US, fmt, it) } ?: ""
private fun Float?.str(fmt: String = "%s"): String = this?.let { String.format(Locale.US, fmt, it) } ?: ""
private fun String.num(): Double? = trim().replace(',', '.').toDoubleOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditDetailsScreen(
    item: GalleryItem,
    settings: StampSettings,
    repo: StampDataRepository,
    onDone: () -> Unit,
) {
    BackHandler(onBack = onDone)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val d0 = item.data

    var lat by remember { mutableStateOf(d0.latitude.str("%.6f")) }
    var lon by remember { mutableStateOf(d0.longitude.str("%.6f")) }
    var search by remember { mutableStateOf("") }
    var title by remember { mutableStateOf(d0.title ?: "") }
    var country by remember { mutableStateOf(d0.countryCode ?: "") }
    var shortAddr by remember { mutableStateOf(d0.shortAddress ?: "") }
    var address by remember { mutableStateOf(d0.address ?: "") }
    var plusCode by remember { mutableStateOf(d0.plusCode ?: "") }
    var altitude by remember { mutableStateOf(d0.altitude.str("%.0f")) }
    var date by remember { mutableStateOf(item.time.toLocalDate()) }
    var clock by remember { mutableStateOf(item.time.toLocalTime().withSecond(0).withNano(0)) }
    var offset by remember { mutableStateOf(DateTimeFormatter.ofPattern("xxx", Locale.US).format(item.time)) }
    var temp by remember { mutableStateOf(d0.temperatureC.str("%.2f")) }
    var humidity by remember { mutableStateOf(d0.humidity?.toString() ?: "") }
    var wind by remember { mutableStateOf(d0.windKmh.str("%.2f")) }
    var pressure by remember { mutableStateOf(d0.pressureHpa.str("%.0f")) }
    var compass by remember { mutableStateOf(d0.azimuthDeg.str("%.0f")) }
    var magnetic by remember { mutableStateOf(d0.magneticUt.str("%.2f")) }
    var sound by remember { mutableStateOf(d0.soundDb.str("%.1f")) }
    var map by remember { mutableStateOf(d0.mapBitmap) }
    var mapType by remember { mutableStateOf(d0.mapType) }
    var working by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    fun zone(): ZoneId = runCatching<ZoneId> { ZoneOffset.of(offset.trim()) }.getOrDefault(ZoneId.systemDefault())
    fun currentTime(): ZonedDateTime = ZonedDateTime.of(date, clock, zone())

    fun formData(): StampData {
        val la = lat.num()
        val lo = lon.num()
        val hasLoc = la != null && lo != null && la in -90.0..90.0 && lo in -180.0..180.0
        return StampData(
            latitude = if (hasLoc) la else null,
            longitude = if (hasLoc) lo else null,
            altitude = altitude.num(),
            title = title.ifBlank { null },
            countryCode = country.trim().uppercase(Locale.US).take(2).ifBlank { null },
            shortAddress = shortAddr.ifBlank { null },
            address = address.ifBlank { null },
            plusCode = plusCode.ifBlank { if (hasLoc) PlusCode.encode(la!!, lo!!) else null },
            temperatureC = temp.num(),
            humidity = humidity.num()?.toInt(),
            windKmh = wind.num(),
            pressureHpa = pressure.num(),
            azimuthDeg = compass.num()?.toFloat()?.let { ((it % 360f) + 360f) % 360f },
            magneticUt = magnetic.num()?.toFloat(),
            soundDb = sound.num()?.toFloat(),
            mapBitmap = map,
            mapType = mapType,
        )
    }

    fun fill(d: StampData) {
        lat = d.latitude.str("%.6f"); lon = d.longitude.str("%.6f")
        title = d.title ?: title; country = d.countryCode ?: country
        shortAddr = d.shortAddress ?: shortAddr; address = d.address ?: address
        plusCode = d.plusCode ?: plusCode
        temp = d.temperatureC?.let { String.format(Locale.US, "%.2f", it) } ?: temp
        humidity = d.humidity?.toString() ?: humidity
        wind = d.windKmh?.let { String.format(Locale.US, "%.2f", it) } ?: wind
        pressure = d.pressureHpa?.let { String.format(Locale.US, "%.0f", it) } ?: pressure
        map = d.mapBitmap ?: map
        mapType = d.mapType
    }

    fun fetch(context: Context) {
        val la = lat.num()
        val lo = lon.num()
        if (la == null || lo == null) {
            Toast.makeText(context, "Enter latitude and longitude first (or search a place)", Toast.LENGTH_SHORT).show()
            return
        }
        working = true
        scope.launch {
            val base = formData().copy(title = null, shortAddress = null, address = null, countryCode = null, plusCode = null)
            val d = withContext(Dispatchers.IO) {
                GalleryStamper.lookup(context, la, lo, currentTime(), settings.mapType, base)
            }
            fill(d)
            working = false
            if (d.address == null) Toast.makeText(context, "Address not found – type it in", Toast.LENGTH_SHORT).show()
            if (d.temperatureC == null) Toast.makeText(context, "Weather not available for that date – type it in", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Edit Details",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            item.data = formData()
                            item.time = currentTime()
                            item.edited = true
                            item.status = ItemStatus.READY
                            onDone()
                        },
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text("Save")
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                // Live watermark preview
                val previewCfg = settings.snapshot()
                val stampPreview = remember(
                    lat, lon, title, country, shortAddr, address, plusCode, altitude, date, clock, offset,
                    temp, humidity, wind, pressure, compass, magnetic, sound, map, previewCfg,
                ) {
                    WatermarkRenderer.render(900, previewCfg, formData(), currentTime(), item.meta.camera)
                }
                stampPreview?.let {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large),
                    ) {
                        Image(
                            it.asImageBitmap(),
                            contentDescription = "Stamp preview",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                SectionCard(title = "Location", icon = Icons.Filled.LocationOn) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field("Latitude", lat, KeyboardType.Decimal, Modifier.weight(1f)) { lat = it }
                        Field("Longitude", lon, KeyboardType.Decimal, Modifier.weight(1f)) { lon = it }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field("Search place or address", search, KeyboardType.Text, Modifier.weight(1f)) { search = it }
                        FilledTonalIconButton(
                            onClick = {
                                if (search.isNotBlank()) {
                                    working = true
                                    scope.launch {
                                        val r = withContext(Dispatchers.IO) { GalleryStamper.searchPlace(context, search) }
                                        working = false
                                        if (r == null) {
                                            Toast.makeText(context, "Place not found", Toast.LENGTH_SHORT).show()
                                        } else {
                                            lat = String.format(Locale.US, "%.6f", r.first)
                                            lon = String.format(Locale.US, "%.6f", r.second)
                                            fetch(context)
                                        }
                                    }
                                }
                            },
                            enabled = !working,
                            modifier = Modifier.size(52.dp),
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = "Find place")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val loc = repo.lastLocation
                                if (loc == null) {
                                    Toast.makeText(context, "Current location isn't known yet", Toast.LENGTH_SHORT).show()
                                } else {
                                    lat = String.format(Locale.US, "%.6f", loc.latitude)
                                    lon = String.format(Locale.US, "%.6f", loc.longitude)
                                    if (loc.hasAltitude()) altitude = String.format(Locale.US, "%.0f", loc.altitude)
                                    fetch(context)
                                }
                            },
                            enabled = !working,
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("My location", maxLines = 1)
                        }
                        Button(
                            onClick = { fetch(context) },
                            enabled = !working,
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (working) {
                                CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("Fetch details", maxLines = 1)
                        }
                    }
                    Text(
                        "Fetch fills in the address, map and weather for these coordinates.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Field("Place title (City, State, Country)", title) { title = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field("Country code (flag)", country, KeyboardType.Text, Modifier.weight(1f)) { country = it.take(2) }
                        Field("Plus Code", plusCode, KeyboardType.Text, Modifier.weight(1.4f)) { plusCode = it }
                    }
                    Field("Short address", shortAddr) { shortAddr = it }
                    Field("Full address", address, singleLine = false) { address = it }
                    Field("Altitude (m)", altitude, KeyboardType.Decimal) { altitude = it }
                }

                SectionCard(title = "Date & time", icon = Icons.Filled.Schedule) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { showDate = true },
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault()).format(date), maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { showTime = true },
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault()).format(clock), maxLines = 1)
                        }
                    }
                    Field("Time zone offset (e.g. +05:30)", offset) { offset = it }
                }

                SectionCard(title = "AI & text extraction", icon = Icons.Filled.AutoAwesome) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        working = true
                                        val bmp = PhotoComposer.decodeUri(context, item.uri)
                                        val text = com.geostamp.camera.ai.VisionAnalyzer.recognizeText(bmp)
                                        bmp.recycle()
                                        working = false
                                        if (!text.isNullOrBlank()) {
                                            val firstLine = text.lines().firstOrNull { it.isNotBlank() } ?: text
                                            settings.note = firstLine.take(80)
                                            Toast.makeText(context, "OCR: $firstLine", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "No text found in photo", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        working = false
                                        Toast.makeText(context, "OCR error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = !working,
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.TextFields, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Read sign", maxLines = 1)
                        }

                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        working = true
                                        val bmp = PhotoComposer.decodeUri(context, item.uri)
                                        val caption = com.geostamp.camera.ai.VisionAnalyzer.autoCaption(bmp)
                                        bmp.recycle()
                                        working = false
                                        if (!caption.isNullOrBlank()) {
                                            settings.note = caption
                                            Toast.makeText(context, "Caption: $caption", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "Could not identify scene", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        working = false
                                        Toast.makeText(context, "Caption error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = !working,
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Auto caption", maxLines = 1)
                        }
                    }
                    Text(
                        "The result goes into the stamp note.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                VoiceMemoSection(
                    photoName = item.name,
                    audioMemoPath = item.audioMemoPath,
                    onAudioRecorded = { path, _ ->
                        item.audioMemoPath = path
                        item.edited = true
                    },
                    onAudioDeleted = {
                        item.audioMemoPath = null
                        item.edited = true
                    },
                    onSpeechTranscribed = { text ->
                        if (text.isNotBlank()) {
                            settings.note = if (settings.note.isBlank()) text else "${settings.note} • $text"
                            Toast.makeText(context, "Transcribed: $text", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                SectionCard(title = "Weather", icon = Icons.Filled.WbSunny) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field("Temperature °C", temp, KeyboardType.Decimal, Modifier.weight(1f)) { temp = it }
                        Field("Humidity %", humidity, KeyboardType.Number, Modifier.weight(1f)) { humidity = it }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field("Wind km/h", wind, KeyboardType.Decimal, Modifier.weight(1f)) { wind = it }
                        Field("Pressure hPa", pressure, KeyboardType.Decimal, Modifier.weight(1f)) { pressure = it }
                    }
                }

                SectionCard(title = "Sensors", icon = Icons.Filled.Explore) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field("Compass °", compass, KeyboardType.Decimal, Modifier.weight(1f)) { compass = it }
                        Field("Magnetic µT", magnetic, KeyboardType.Decimal, Modifier.weight(1f)) { magnetic = it }
                        Field("Sound dB", sound, KeyboardType.Decimal, Modifier.weight(1f)) { sound = it }
                    }
                    Text(
                        "Note, person name, phone, logo and numbering come from the Stamp Template settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showDate) {
        val dp = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dp.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = dp) }
    }
    if (showTime) {
        val tp = rememberTimePickerState(initialHour = clock.hour, initialMinute = clock.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    clock = LocalTime.of(tp.hour, tp.minute)
                    showTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancel") } },
            text = { TimePicker(state = tp) },
        )
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    keyboard: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier.fillMaxWidth(),
    singleLine: Boolean = true,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        shape = MaterialTheme.shapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = modifier,
    )
}

@Composable
private fun PhotoMapView(
    items: List<GalleryItem>,
    onOpenInMap: (Double, Double, String) -> Unit,
    onStamp: (GalleryItem) -> Unit,
    onShareWhatsApp: (GalleryItem) -> Unit,
) {
    val geoItems = items.filter { it.data.latitude != null && it.data.longitude != null }
    val unmappedItems = items.filter { it.data.latitude == null || it.data.longitude == null }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (geoItems.isEmpty()) {
            item {
                ElevatedCard(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Default.LocationOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "No photos with GPS coordinates yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Pick photos containing geotags, or tap 'Use current GPS' above to assign your position to photos.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        } else {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Map, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${geoItems.size} geo-tagged photos plotted on map",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            items(geoItems, key = { "map_" + it.uri.toString() + it.hashCode() }) { item ->
                MapItemCard(
                    item = item,
                    onOpenInMap = {
                        val lat = item.data.latitude ?: return@MapItemCard
                        val lon = item.data.longitude ?: return@MapItemCard
                        val label = item.data.title ?: item.name
                        onOpenInMap(lat, lon, label)
                    },
                    onStamp = { onStamp(item) },
                    onShareWhatsApp = { onShareWhatsApp(item) },
                )
            }
        }

        if (unmappedItems.isNotEmpty()) {
            item {
                Text(
                    "⚠️ ${unmappedItems.size} photo(s) without GPS coordinates",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun MapItemCard(
    item: GalleryItem,
    onOpenInMap: () -> Unit,
    onStamp: () -> Unit,
    onShareWhatsApp: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row {
                Box(
                    Modifier
                        .size(88.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    val tile = item.data.mapBitmap
                    if (tile != null && !tile.isRecycled) {
                        Image(tile.asImageBitmap(), "Map tile", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else if (item.thumb != null && !item.thumb.isRecycled) {
                        Image(item.thumb.asImageBitmap(), "Photo thumb", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    val lat = item.data.latitude
                    val lon = item.data.longitude
                    if (lat != null && lon != null) {
                        InfoLine(icon = Icons.Filled.LocationOn, text = String.format(Locale.US, "%.5f, %.5f", lat, lon))
                    }
                    item.data.plusCode?.let { InfoLine(icon = Icons.Filled.Public, text = it) }
                    item.data.title?.let { InfoLine(icon = Icons.Filled.Business, text = it) }
                    InfoLine(
                        icon = Icons.Filled.Schedule,
                        text = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(item.time),
                    )
                }
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onOpenInMap,
                    modifier = Modifier.padding(end = 6.dp),
                ) {
                    Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open map", style = MaterialTheme.typography.labelLarge)
                }
                if (item.status == ItemStatus.SAVED) {
                    Button(onClick = onShareWhatsApp) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("WhatsApp", style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    Button(
                        onClick = onStamp,
                        enabled = item.status != ItemStatus.SAVING && item.status != ItemStatus.LOOKING_UP,
                    ) {
                        Text("Stamp", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
