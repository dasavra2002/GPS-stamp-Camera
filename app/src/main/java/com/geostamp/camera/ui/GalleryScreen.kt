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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LocationOff
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
                PhotoSaver.save(context, stamped, item.data, item.time, prefix = "Stamped_$base", subFolder = cfg.siteName.ifBlank { null }).also {
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
                        "Stamp Gallery Photos",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // Header description
            Text(
                "Pick photos from your phone. Location, date/time and orientation are extracted from EXIF; address, map and weather are looked up. Missing data can be added with Edit Details. Originals remain untouched.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
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
                    Text("Pick Photos", style = MaterialTheme.typography.labelLarge)
                }
                val stampable = state.items.filter { it.status == ItemStatus.READY }
                FilledTonalButton(
                    onClick = { state.scope.launch { stampable.forEach { stamp(it) } } },
                    enabled = stampable.isNotEmpty(),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Stamp All (${stampable.size})", style = MaterialTheme.typography.labelLarge)
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
                        label = { Text("PDF Report") },
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
                        label = { Text("CSV Export") },
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
                        label = { Text("Google Earth (KML)") },
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
                        label = { Text("Drive Backup") },
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
                        Text("Photo Map (${state.items.count { it.data.latitude != null }})")
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

            if (viewMode == GalleryViewMode.LIST) {
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
    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row {
                Box(
                    Modifier
                        .size(86.dp)
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
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    val d = item.data
                    Text(
                        if (d.latitude != null) String.format(Locale.US, "📍 %.5f, %.5f", d.latitude, d.longitude) +
                            (if (item.meta.hasLocation && !item.edited) " (from photo)" else " (entered)")
                        else "📍 No location",
                        color = if (d.latitude != null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "🕒 " + DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(item.time) +
                            when {
                                item.edited -> ""
                                item.meta.timeFromExif -> " (from photo)"
                                item.meta.time != null -> " (file date)"
                                else -> " (unknown – set it)"
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    d.title?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    if (item.audioMemoPath != null) {
                        Text(
                            "🎙️ Voice Memo Attached",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        item.status.text,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (item.status == ItemStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onRemove) {
                    Text("Remove", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onEdit, enabled = item.status != ItemStatus.SAVING) {
                    Text("Edit Details", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                TextButton(onClick = onPreview) {
                    Text("Preview", style = MaterialTheme.typography.labelMedium)
                }
                if (item.status == ItemStatus.SAVED) {
                    FilledTonalButton(onClick = onOpen) {
                        Text("Open", style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    Button(
                        onClick = onStamp,
                        enabled = item.status != ItemStatus.SAVING && item.status != ItemStatus.LOOKING_UP && item.status != ItemStatus.READING,
                    ) {
                        Text("Stamp", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
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
            // Live watermark preview card
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
                        .padding(16.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                ) {
                    Image(
                        it.asImageBitmap(),
                        contentDescription = "Stamp preview",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Location Section Card
                ElevatedCard(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Location", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Field("Latitude", lat, KeyboardType.Decimal, Modifier.weight(1f)) { lat = it }
                            Field("Longitude", lon, KeyboardType.Decimal, Modifier.weight(1f)) { lon = it }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Field("Search place or address", search, KeyboardType.Text, Modifier.weight(1f)) { search = it }
                            FilledTonalButton(onClick = {
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
                            }) { Text("Find") }
                        }
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
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Use My Current Location")
                        }
                        Button(
                            onClick = { fetch(context) },
                            enabled = !working,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (working) CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            else Text("Fetch Address, Map & Weather")
                        }
                        Field("Place title (City, State, Country)", title) { title = it }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Field("Country code (flag)", country, KeyboardType.Text, Modifier.weight(1f)) { country = it.take(2) }
                            Field("Plus Code", plusCode, KeyboardType.Text, Modifier.weight(1.4f)) { plusCode = it }
                        }
                        Field("Short address", shortAddr) { shortAddr = it }
                        Field("Full address", address, singleLine = false) { address = it }
                        Field("Altitude (m)", altitude, KeyboardType.Decimal) { altitude = it }
                    }
                }

                // Date & Time Section Card
                ElevatedCard(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Date & Time", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { showDate = true }, modifier = Modifier.weight(1f)) {
                                Text(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault()).format(date))
                            }
                            OutlinedButton(onClick = { showTime = true }, modifier = Modifier.weight(1f)) {
                                Text(DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault()).format(clock))
                            }
                        }
                        Field("Time zone offset (e.g. +05:30)", offset) { offset = it }
                    }
                }

                // AI & OCR Card
                ElevatedCard(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("AI & Text Extraction", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
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
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("🔤 OCR Signboard")
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
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("🏷️ Auto Caption")
                            }
                        }
                    }
                }

                // Voice Note / Audio Memo Card
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

                // Weather Card
                ElevatedCard(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Weather", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Field("Temperature °C", temp, KeyboardType.Decimal, Modifier.weight(1f)) { temp = it }
                            Field("Humidity %", humidity, KeyboardType.Number, Modifier.weight(1f)) { humidity = it }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Field("Wind km/h", wind, KeyboardType.Decimal, Modifier.weight(1f)) { wind = it }
                            Field("Pressure hPa", pressure, KeyboardType.Decimal, Modifier.weight(1f)) { pressure = it }
                        }
                    }
                }

                // Sensors Card
                ElevatedCard(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Sensors", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
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
                }

                Spacer(Modifier.height(32.dp))
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
    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
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
                        Text(
                            String.format(Locale.US, "📍 %.5f, %.5f", lat, lon),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    item.data.plusCode?.let {
                        Text("🌐 $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    item.data.title?.let {
                        Text("🏢 $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                    }
                    Text(
                        "🕒 " + DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(item.time),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    Text("🗺️ Map View", style = MaterialTheme.typography.labelMedium)
                }
                if (item.status == ItemStatus.SAVED) {
                    Button(
                        onClick = onShareWhatsApp,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366), contentColor = Color.White),
                    ) {
                        Text("💬 WhatsApp", style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    Button(
                        onClick = onStamp,
                        enabled = item.status != ItemStatus.SAVING && item.status != ItemStatus.LOOKING_UP,
                    ) {
                        Text("⚡ Stamp", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
