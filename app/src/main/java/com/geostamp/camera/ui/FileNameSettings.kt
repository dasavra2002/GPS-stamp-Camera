package com.geostamp.camera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.geostamp.camera.data.FileNameField
import com.geostamp.camera.data.FileNameSeparator
import com.geostamp.camera.data.FileNameValues
import com.geostamp.camera.data.FileNamer
import com.geostamp.camera.data.FileNaming
import com.geostamp.camera.data.StampData
import com.geostamp.camera.data.StampSettings
import java.time.ZonedDateTime

/** Quick starting points for the file-name template. */
private enum class FileNamePreset(val label: String, val fields: List<FileNameField>) {
    DEFAULT("Default", FileNaming.DEFAULT.fields),
    COORDS("Coordinates", listOf(FileNameField.PREFIX, FileNameField.DATE_TIME, FileNameField.LAT_LONG)),
    PLUS_CODE("Plus Code", listOf(FileNameField.PREFIX, FileNameField.DATE_TIME, FileNameField.PLUS_CODE)),
    SURVEY("Survey", listOf(FileNameField.SITE, FileNameField.NUMBER, FileNameField.DATE_TIME, FileNameField.LAT_LONG, FileNameField.ALTITUDE)),
    PLACE("Place", listOf(FileNameField.DATE, FileNameField.PLACE, FileNameField.PLUS_CODE, FileNameField.DATE_TIME)),
}

/** Settings card: pick and order what goes into a saved photo's file name, with a live preview. */
@Composable
fun FileNameSettingsCard(settings: StampSettings, data: StampData) {
    SectionCard(title = "Photo file name", contentPadding = PaddingValuesVertical, spacing = 0.dp) {
        val fields = settings.fileNameFields
        val sampleLocation = data.latitude == null || data.longitude == null
        val values = FileNameValues(
            time = ZonedDateTime.now(),
            latitude = data.latitude ?: 12.97160,
            longitude = data.longitude ?: 77.59460,
            altitude = data.altitude ?: if (sampleLocation) 920.0 else null,
            plusCode = data.plusCode,
            place = data.title ?: if (sampleLocation) "Bengaluru, Karnataka, India" else null,
            shortAddress = data.shortAddress ?: if (sampleLocation) "MG Road, Bengaluru" else null,
            countryCode = data.countryCode ?: if (sampleLocation) "IN" else null,
            site = settings.siteName,
            person = settings.personName,
            custom1 = settings.custom1Value,
            number = settings.nextNumber,
        )
        val preview = FileNamer.fileName(settings.fileNaming, values)

        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("Preview", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                preview,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
            Text(
                (if (sampleLocation) "Using a sample location until GPS has a fix. " else "") +
                    "Fields without a value are skipped. If two photos get the same name, a number is added.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FileNamePreset.entries.forEach { p ->
                AssistChip(onClick = { settings.fileNameFields = p.fields }, label = { Text(p.label) })
            }
        }

        if (FileNameField.PREFIX in fields) {
            OutlinedTextField(
                value = settings.fileNamePrefix,
                onValueChange = { settings.fileNamePrefix = it.take(40) },
                label = { Text("Prefix text") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        ChoiceRow("Separator", settings.fileNameSeparator, FileNameSeparator.entries) { settings.fileNameSeparator = it }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        SubHeader("In the name, in order")
        if (fields.isEmpty()) {
            Text(
                "Nothing selected: photos are named by date and time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        fields.forEachIndexed { i, f ->
            FieldOrderRow(
                field = f,
                canUp = i > 0,
                canDown = i < fields.lastIndex,
                onUp = { settings.fileNameFields = fields.toMutableList().apply { add(i - 1, removeAt(i)) } },
                onDown = { settings.fileNameFields = fields.toMutableList().apply { add(i + 1, removeAt(i)) } },
                onRemove = { settings.fileNameFields = fields - f },
            )
        }

        val available = FileNameField.entries.filter { it !in fields }
        if (available.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            SubHeader("Add to the name")
            available.forEach { f ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(f.label, style = MaterialTheme.typography.bodyLarge)
                        Text(f.example, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { settings.fileNameFields = fields + f }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add ${f.label}", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

private val PaddingValuesVertical = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)

@Composable
private fun SubHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun FieldOrderRow(
    field: FileNameField,
    canUp: Boolean,
    canDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(field.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onUp, enabled = canUp) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move ${field.label} earlier")
        }
        IconButton(onClick = onDown, enabled = canDown) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move ${field.label} later")
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = "Remove ${field.label}", modifier = Modifier.size(20.dp))
        }
    }
}
