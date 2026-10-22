package com.geostamp.camera.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import com.geostamp.camera.location.FixCheck
import com.geostamp.camera.stamp.CaptureProof
import com.geostamp.camera.stamp.DeviceKey
import com.geostamp.camera.stamp.KeyTrust
import com.geostamp.camera.stamp.ProofCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.util.Locale

private sealed interface VerifyState {
    data object Picking : VerifyState
    data object Checking : VerifyState
    data object NoProof : VerifyState
    data class Done(val check: ProofCheck) : VerifyState
}

/** Picks a photo and checks its signed capture proof. */
@Composable
fun VerifyPhotoDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<VerifyState>(VerifyState.Picking) }

    // OpenDocument hands over the file as stored; some picker paths re-encode photos, which breaks the proof.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            onDismiss()
            return@rememberLauncherForActivityResult
        }
        state = VerifyState.Checking
        scope.launch {
            val check = withContext(Dispatchers.Default) { checkPhoto(context, uri) }
            state = if (check == null) VerifyState.NoProof else VerifyState.Done(check)
        }
    }
    LaunchedEffect(Unit) { picker.launch(arrayOf("image/jpeg")) }

    if (state == VerifyState.Picking) return
    val shown = state
    val verdictIcon: @Composable () -> Unit = {
        val authentic = (shown as? VerifyState.Done)?.check?.authentic
        Icon(
            when (authentic) {
                true -> Icons.Filled.VerifiedUser
                false -> Icons.Filled.GppBad
                null -> Icons.Filled.GppMaybe
            },
            contentDescription = null,
            tint = when (authentic) {
                true -> MaterialTheme.colorScheme.tertiary
                false -> MaterialTheme.colorScheme.error
                null -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(40.dp),
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            if (state !is VerifyState.Checking) {
                TextButton(onClick = { picker.launch(arrayOf("image/jpeg")) }) { Text("Check another") }
            }
        },
        icon = if (state is VerifyState.Done || state == VerifyState.NoProof) verdictIcon else null,
        title = {
            Text(
                when (val s = state) {
                    is VerifyState.Done -> if (s.check.authentic) "Authentic GeoStamp photo" else "Could not verify"
                    VerifyState.NoProof -> "No GeoStamp proof"
                    else -> "Checking photo…"
                },
                textAlign = TextAlign.Center,
            )
        },
        text = {
            when (val s = state) {
                VerifyState.Checking, VerifyState.Picking -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Checking signature and image data")
                }
                VerifyState.NoProof -> Text(
                    "This photo has no capture proof. It was taken with another app, taken with an older " +
                        "version of GeoStamp, or its metadata was removed (for example by a messaging app).",
                )
                is VerifyState.Done -> ProofDetails(s.check)
            }
        },
    )
}

@Composable
private fun ProofDetails(c: ProofCheck) {
    val p = c.payload
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CheckRow(c.signatureValid, "Signature valid", "The details below are exactly what was signed.", "The signature doesn't match; details were changed.")
        CheckRow(c.imageUnchanged, "Image unchanged", "Pixels are identical to when it was saved.", "The image was edited, cropped or re-compressed after saving.")
        CheckRow(
            when (c.keyTrust) {
                KeyTrust.HARDWARE_THIS_APP -> true
                KeyTrust.HARDWARE_OTHER_APP -> false
                KeyTrust.NOT_ATTESTED -> null
            },
            "Signed on a real phone",
            "Key is locked in the phone's secure hardware and was made by this app.",
            when (c.keyTrust) {
                KeyTrust.HARDWARE_OTHER_APP -> "Key was made by a different app or a differently signed build of GeoStamp."
                else -> "Google hardware attestation is missing, so the key could have been made anywhere."
            },
        )
        p.mock?.let {
            CheckRow(!it, "Real location", "Not from a mock-location app.", "The location came from a mock-location (fake GPS) app.")
        }
        c.verifiedBoot?.let {
            CheckRow(it, "Phone not modified", "Bootloader locked and system verified at boot.", "Bootloader unlocked or system modified (rooted phones can fake GPS).")
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Detail("Time", p.time)
        if (p.latitude != null && p.longitude != null) {
            Detail("Location", String.format(Locale.US, "%.6f, %.6f", p.latitude, p.longitude))
        }
        p.accuracyM?.let { Detail("Accuracy", String.format(Locale.US, "±%.1f m", it)) }
        p.fixAgeS?.let { Detail("Fix age", FixCheck.formatAge(it * 1000) + " before saving") }
        p.altitude?.let { Detail("Altitude", String.format(Locale.US, "%.1f m", it)) }
        Detail("Location from", sourceLabel(p.locationSource))
        Detail("Photo number", p.number.toString())
    }
}

@Composable
private fun CheckRow(ok: Boolean?, title: String, good: String, bad: String) {
    val (icon, tint) = when (ok) {
        true -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.tertiary
        false -> Icons.Filled.Error to MaterialTheme.colorScheme.error
        null -> Icons.AutoMirrored.Filled.Help to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(if (ok == true) good else bad, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(110.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun sourceLabel(source: String): String = when {
    source.startsWith("bluetooth-gnss:") -> "External GNSS receiver (" + source.substringAfter(':').replace('-', ' ').uppercase(Locale.US) + "), live at capture"
    else -> sourceLabelPlain(source)
}

private fun sourceLabelPlain(source: String): String = when (source) {
    "phone-gps" -> "Phone GPS, live at capture"
    "bluetooth-gnss" -> "External GNSS receiver, live at capture"
    "gallery-exif" -> "An existing photo's EXIF (stamped later)"
    "gallery-manual" -> "Typed in by hand (stamped later)"
    "none" -> "No location"
    else -> source
}

private fun checkPhoto(context: Context, uri: Uri): ProofCheck? {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    val xmp = try {
        ExifInterface(ByteArrayInputStream(bytes)).getAttribute(ExifInterface.TAG_XMP)
    } catch (e: Exception) {
        null
    }
    val record = CaptureProof.fromXmp(xmp) ?: return null
    return CaptureProof.verify(record, bytes, DeviceKey.attestationRoots(context), DeviceKey.appSigningDigests(context))
}
