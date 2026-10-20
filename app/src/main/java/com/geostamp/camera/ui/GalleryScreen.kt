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
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
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
}

/** Survives switching between the camera and gallery screens. */
class GalleryState(
    /** App-level scope, so lookups and saving finish even if you leave the gallery screen. */
    val scope: CoroutineScope,
) {
    val items = mutableStateListOf<GalleryItem>()
    var editing by mutableStateOf<GalleryItem?>(null)
    var busy by mutableStateOf(false)
}

private val Card = Color(0xFF1C1C1E)
private val Muted = Color(0xFF9E9E9E)

private fun mediaPermissions(): Array<String> {
    val list = mutableListOf<String>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) list += Manifest.permission.READ_MEDIA_IMAGES
    else list += Manifest.permission.READ_EXTERNAL_STORAGE
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) list += Manifest.permission.ACCESS_MEDIA_LOCATION
    return list.toTypedArray()
}

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

    fun shareItemWhatsApp(item: GalleryItem) {
        val uri = item.savedUri ?: item.uri
        val text = buildString {
            append("📍 GeoStamp Photo\n")
            item.data.title?.let { append("🏢 $it\n") }
            if (item.data.latitude != null && item.data.longitude != null) {
                append("🗺 GPS: ${item.data.latitude}, ${item.data.longitude}\n")
            }
            item.data.plusCode?.let { append("🌐 Plus Code: $it\n") }
            append("⏰ Time: ${item.time.format(DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a"))}\n")
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
        val geo = Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(label)})")
        val intent = Intent(Intent.ACTION_VIEW, geo)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=$lat,$lon")))
        }
    }

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
            // Fetch address, map and weather for photos that have GPS.
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

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
            Text("Stamp gallery photos", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            "Pick photos already on your phone. Location, date/time and direction are read from each photo's " +
                "metadata; address, map and weather for that place and time are looked up. Anything missing " +
                "can be filled in with Edit details. Originals are never changed.",
            color = Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { pick() }, enabled = !state.busy, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black)) {
                Text("Pick photos")
            }
            val stampable = state.items.filter { it.status == ItemStatus.READY }
            OutlinedButton(
                onClick = { state.scope.launch { stampable.forEach { stamp(it) } } },
                enabled = stampable.isNotEmpty(),
            ) { Text("Stamp all (${stampable.size})", color = Color.White) }
        }
        if (state.items.any { it.data.latitude == null }) {
            TextButton(onClick = { useCurrentLocationForMissing() }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text("Use my current location for photos without GPS", color = Accent)
            }
        }

        if (state.items.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        state.scope.launch {
                            Toast.makeText(context, "Generating inspection PDF report…", Toast.LENGTH_SHORT).show()
                            val uri = ReportGenerator.generatePdfReport(
                                context, state.items,
                                projectName = settings.siteName.ifBlank { settings.note.ifBlank { "Site Visit" } },
                                inspectorName = settings.personName,
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
                    },
                ) { Text("📄 PDF Report", color = Color.White) }

                OutlinedButton(
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
                ) { Text("📊 CSV Export", color = Color.White) }

                OutlinedButton(
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
                ) { Text("🌍 Google Earth (KML)", color = Color.White) }

                OutlinedButton(
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
                ) { Text("☁️ Drive Backup", color = Color.White) }
            }
        }

        if (state.items.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { viewMode = GalleryViewMode.LIST },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (viewMode == GalleryViewMode.LIST) Accent else Color(0x33FFFFFF),
                        contentColor = if (viewMode == GalleryViewMode.LIST) Color.Black else Color.White,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("📋 List (${state.items.size})")
                }
                Button(
                    onClick = { viewMode = GalleryViewMode.MAP },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (viewMode == GalleryViewMode.MAP) Accent else Color(0x33FFFFFF),
                        contentColor = if (viewMode == GalleryViewMode.MAP) Color.Black else Color.White,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("🗺️ Map (${state.items.count { it.data.latitude != null }})")
                }
            }
        }

        if (state.busy) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), color = Accent, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Reading photos…", color = Color.White)
            }
        }

        if (viewMode == GalleryViewMode.LIST) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)) {
                items(state.items, key = { it.uri.toString() + it.hashCode() }) { item ->
                    ItemCard(
                        item = item,
                        onEdit = { state.editing = item },
                        onPreview = {
                            scope.launch {
                                val cfg = settings.snapshot()
                                previewBitmap = withContext(Dispatchers.Default) {
                                    runCatching {
                                        // Same pipeline as saving, just scaled down for the screen.
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

    previewBitmap?.let { bmp ->
        AlertDialog(
            onDismissRequest = { previewBitmap = null },
            confirmButton = { TextButton(onClick = { previewBitmap = null }) { Text("Close") } },
            text = { Image(bmp.asImageBitmap(), "Stamped preview", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth()) },
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
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Card)
            .padding(12.dp),
    ) {
        Row {
            Box(
                Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.DarkGray),
            ) {
                item.thumb?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                val d = item.data
                Text(
                    if (d.latitude != null) String.format(Locale.US, "📍 %.5f, %.5f", d.latitude, d.longitude) +
                        (if (item.meta.hasLocation && !item.edited) " (from photo)" else " (entered)")
                    else "📍 No location",
                    color = if (d.latitude != null) Color(0xFF81C784) else Color(0xFFFFB74D), fontSize = 12.sp,
                )
                Text(
                    "🕒 " + DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(item.time) +
                        when {
                            item.edited -> ""
                            item.meta.timeFromExif -> " (from photo)"
                            item.meta.time != null -> " (file date)"
                            else -> " (unknown – set it)"
                        },
                    color = Muted, fontSize = 12.sp,
                )
                d.title?.let { Text(it, color = Muted, fontSize = 12.sp, maxLines = 1) }
                Text(item.status.text, color = if (item.status == ItemStatus.FAILED) Color(0xFFE57373) else Accent, fontSize = 12.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onRemove) { Text("Remove", color = Muted) }
            TextButton(onClick = onEdit, enabled = item.status != ItemStatus.SAVING) { Text("Edit details", color = Accent) }
            TextButton(onClick = onPreview) { Text("Preview", color = Color.White) }
            if (item.status == ItemStatus.SAVED) {
                TextButton(onClick = onOpen) { Text("Open", color = Color.White) }
            } else {
                TextButton(
                    onClick = onStamp,
                    enabled = item.status != ItemStatus.SAVING && item.status != ItemStatus.LOOKING_UP &&
                        item.status != ItemStatus.READING,
                ) { Text("Stamp", color = Color.White) }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------
// Edit details: fill in or correct anything the photo's metadata is missing.
// ------------------------------------------------------------------------------------------

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
            // Start from blank address fields so the lookup replaces them.
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

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancel", tint = Color.White) }
            Text("Edit details", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                item.data = formData()
                item.time = currentTime()
                item.edited = true
                item.status = ItemStatus.READY
                onDone()
            }) { Text("Save", color = Accent, fontWeight = FontWeight.Bold) }
        }

        // Live preview of the stamp with the values typed so far
        val previewCfg = settings.snapshot()
        val stampPreview = remember(
            lat, lon, title, country, shortAddr, address, plusCode, altitude, date, clock, offset,
            temp, humidity, wind, pressure, compass, magnetic, sound, map, previewCfg,
        ) {
            WatermarkRenderer.render(900, previewCfg, formData(), currentTime(), item.meta.camera)
        }
        stampPreview?.let {
            Image(
                it.asImageBitmap(), "Stamp preview", contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2B3A4A))
                    .padding(8.dp),
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Section("Location")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Latitude", lat, KeyboardType.Decimal, Modifier.weight(1f)) { lat = it }
                Field("Longitude", lon, KeyboardType.Decimal, Modifier.weight(1f)) { lon = it }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Search place or address", search, KeyboardType.Text, Modifier.weight(1f)) { search = it }
                OutlinedButton(onClick = {
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
                }) { Text("Find", color = Color.White) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val loc = repo.lastLocation
                    if (loc == null) {
                        Toast.makeText(context, "Current location isn't known yet", Toast.LENGTH_SHORT).show()
                    } else {
                        lat = String.format(Locale.US, "%.6f", loc.latitude)
                        lon = String.format(Locale.US, "%.6f", loc.longitude)
                        if (loc.hasAltitude()) altitude = String.format(Locale.US, "%.0f", loc.altitude)
                        fetch(context)
                    }
                }) { Text("Use my current location", color = Color.White) }
            }
            Button(
                onClick = { fetch(context) },
                enabled = !working,
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (working) CircularProgressIndicator(Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
                else Text("Fetch address, map & weather")
            }
            Field("Place title (City, State, Country)", title) { title = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Country code (flag)", country, KeyboardType.Text, Modifier.weight(1f)) { country = it.take(2) }
                Field("Plus Code", plusCode, KeyboardType.Text, Modifier.weight(1.4f)) { plusCode = it }
            }
            Field("Short address", shortAddr) { shortAddr = it }
            Field("Full address", address, singleLine = false) { address = it }
            Field("Altitude (m)", altitude, KeyboardType.Decimal) { altitude = it }

            Section("Date & time")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { showDate = true }, modifier = Modifier.weight(1f)) {
                    Text(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault()).format(date), color = Color.White)
                }
                OutlinedButton(onClick = { showTime = true }, modifier = Modifier.weight(1f)) {
                    Text(DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault()).format(clock), color = Color.White)
                }
            }
            Field("Time zone offset (e.g. +05:30)", offset) { offset = it }

            Section("AI & Text Extraction")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
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
                    Text("🔤 OCR Signboard", color = Color.White, fontSize = 12.sp)
                }

                OutlinedButton(
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
                    Text("🏷️ Auto Caption", color = Color.White, fontSize = 12.sp)
                }
            }

            Section("Weather")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Temperature °C", temp, KeyboardType.Decimal, Modifier.weight(1f)) { temp = it }
                Field("Humidity %", humidity, KeyboardType.Number, Modifier.weight(1f)) { humidity = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Wind km/h", wind, KeyboardType.Decimal, Modifier.weight(1f)) { wind = it }
                Field("Pressure hPa", pressure, KeyboardType.Decimal, Modifier.weight(1f)) { pressure = it }
            }

            Section("Sensors")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Compass °", compass, KeyboardType.Decimal, Modifier.weight(1f)) { compass = it }
                Field("Magnetic µT", magnetic, KeyboardType.Decimal, Modifier.weight(1f)) { magnetic = it }
                Field("Sound dB", sound, KeyboardType.Decimal, Modifier.weight(1f)) { sound = it }
            }
            Text(
                "Note, person name, phone, logo and numbering come from the Stamp Template settings.",
                color = Muted, fontSize = 12.sp,
            )
            Spacer(Modifier.height(40.dp))
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
private fun Section(title: String) {
    Text(title, color = Accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
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
        label = { Text(label, fontSize = 12.sp) },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        colors = darkFieldColors(),
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
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp, top = 8.dp),
    ) {
        if (geoItems.isEmpty()) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Default.LocationOff,
                        contentDescription = null,
                        tint = Muted,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No photos with GPS coordinates yet",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Pick photos containing geotags, or tap 'Use my current location' to assign your position to photos.",
                        color = Muted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Map, contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${geoItems.size} geo-tagged photos plotted",
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
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
                Spacer(Modifier.height(16.dp))
                Text(
                    "⚠️ ${unmappedItems.size} photo(s) without GPS coordinates",
                    color = Color(0xFFFFB74D),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
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
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Card)
            .padding(12.dp),
    ) {
        Row {
            Box(
                Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.DarkGray),
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
                Text(item.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, fontSize = 14.sp)
                val lat = item.data.latitude
                val lon = item.data.longitude
                if (lat != null && lon != null) {
                    Text(
                        String.format(Locale.US, "📍 %.5f, %.5f", lat, lon),
                        color = Color(0xFF81C784),
                        fontSize = 12.sp,
                    )
                }
                item.data.plusCode?.let {
                    Text("🌐 $it", color = Muted, fontSize = 11.sp, maxLines = 1)
                }
                item.data.title?.let {
                    Text("🏢 $it", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1)
                }
                Text(
                    "🕒 " + DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(item.time),
                    color = Muted,
                    fontSize = 11.sp,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(
                onClick = onOpenInMap,
                modifier = Modifier.padding(end = 6.dp),
            ) {
                Text("🗺️ Map", color = Color.White, fontSize = 12.sp)
            }
            if (item.status == ItemStatus.SAVED) {
                Button(
                    onClick = onShareWhatsApp,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366), contentColor = Color.White),
                ) {
                    Text("💬 WhatsApp", fontSize = 12.sp)
                }
            } else {
                Button(
                    onClick = onStamp,
                    enabled = item.status != ItemStatus.SAVING && item.status != ItemStatus.LOOKING_UP,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black),
                ) {
                    Text("⚡ Stamp", fontSize = 12.sp)
                }
            }
        }
    }
}

