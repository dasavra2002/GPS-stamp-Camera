package com.geostamp.camera.ui

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

data class SignatureStroke(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float,
)

class SignaturePadState {
    val strokes = mutableStateListOf<SignatureStroke>()
    var currentPoints = mutableStateListOf<Offset>()
    var selectedColor by mutableStateOf(Color(0xFF0A2540)) // Professional Navy
    var strokeWidthPx by mutableStateOf(5.5f)
    var canvasSize by mutableStateOf(IntSize.Zero)

    val hasSignature: Boolean
        get() = strokes.isNotEmpty() || currentPoints.isNotEmpty()

    fun addPoint(point: Offset) {
        currentPoints.add(point)
    }

    fun finishStroke() {
        if (currentPoints.size > 1) {
            strokes.add(
                SignatureStroke(
                    points = currentPoints.toList(),
                    color = selectedColor,
                    strokeWidth = strokeWidthPx,
                )
            )
        }
        currentPoints.clear()
    }

    fun undo() {
        if (strokes.isNotEmpty()) {
            strokes.removeAt(strokes.lastIndex)
        }
    }

    fun clear() {
        strokes.clear()
        currentPoints.clear()
    }

    fun createBitmap(targetWidth: Int = 400, targetHeight: Int = 180): Bitmap? {
        if (!hasSignature && strokes.isEmpty()) return null
        val w = if (canvasSize.width > 0) canvasSize.width else targetWidth
        val h = if (canvasSize.height > 0) canvasSize.height else targetHeight
        if (w <= 0 || h <= 0) return null

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        for (stroke in strokes) {
            if (stroke.points.size < 2) continue
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = stroke.color.toArgb()
                strokeWidth = stroke.strokeWidth
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val path = AndroidPath()
            val pts = stroke.points
            path.moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) {
                val pPrev = pts[i - 1]
                val pCurr = pts[i]
                val midX = (pPrev.x + pCurr.x) / 2f
                val midY = (pPrev.y + pCurr.y) / 2f
                path.quadTo(pPrev.x, pPrev.y, midX, midY)
            }
            pts.lastOrNull()?.let { path.lineTo(it.x, it.y) }
            canvas.drawPath(path, paint)
        }
        return bitmap
    }
}

// The pad is always paper-white, whatever the app theme, because the ink is dark navy and the
// signature is rendered onto a white PDF page.
private val PadInk = Color(0xFF6B7280)

@Composable
fun SignaturePad(
    state: SignaturePadState,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFFF9FAFB),
) {
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .onSizeChanged { state.canvasSize = it }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        state.addPoint(offset)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        state.addPoint(change.position)
                    },
                    onDragEnd = {
                        state.finishStroke()
                    },
                    onDragCancel = {
                        state.finishStroke()
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            // Draw guideline
            val lineY = size.height * 0.78f
            drawLine(
                color = PadInk.copy(alpha = 0.35f),
                start = Offset(24.dp.toPx(), lineY),
                end = Offset(size.width - 24.dp.toPx(), lineY),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Draw historical strokes
            for (stroke in state.strokes) {
                if (stroke.points.size < 2) continue
                val path = Path()
                val pts = stroke.points
                path.moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) {
                    val pPrev = pts[i - 1]
                    val pCurr = pts[i]
                    val midX = (pPrev.x + pCurr.x) / 2f
                    val midY = (pPrev.y + pCurr.y) / 2f
                    path.quadraticTo(pPrev.x, pPrev.y, midX, midY)
                }
                pts.lastOrNull()?.let { path.lineTo(it.x, it.y) }

                drawPath(
                    path = path,
                    color = stroke.color,
                    style = Stroke(
                        width = stroke.strokeWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // Draw in-progress stroke
            if (state.currentPoints.size >= 2) {
                val path = Path()
                val pts = state.currentPoints
                path.moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) {
                    val pPrev = pts[i - 1]
                    val pCurr = pts[i]
                    val midX = (pPrev.x + pCurr.x) / 2f
                    val midY = (pPrev.y + pCurr.y) / 2f
                    path.quadraticTo(pPrev.x, pPrev.y, midX, midY)
                }
                pts.lastOrNull()?.let { path.lineTo(it.x, it.y) }

                drawPath(
                    path = path,
                    color = state.selectedColor,
                    style = Stroke(
                        width = state.strokeWidthPx,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }

        // Placeholder text when empty
        if (!state.hasSignature) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(8.dp)
            ) {
                Icon(Icons.Filled.Gesture, contentDescription = null, tint = PadInk, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Sign here",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PadInk,
                )
            }
        }

        // Signing-line mark
        Icon(
            Icons.Filled.Close,
            contentDescription = null,
            tint = PadInk.copy(alpha = 0.6f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 10.dp, bottom = 12.dp)
                .size(16.dp)
        )
    }
}

/** Complete dialog to sign inspection reports with dual Inspector & Client sign-off. */
data class InspectionSignoff(
    val inspectorName: String,
    val inspectorRole: String,
    val inspectorSignature: Bitmap?,
    val clientName: String,
    val clientCompany: String,
    val clientSignature: Bitmap?,
    val approvalStatus: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DigitalSignatureDialog(
    initialInspectorName: String = "",
    initialCompanyName: String = "",
    onDismiss: () -> Unit,
    onConfirm: (InspectionSignoff) -> Unit,
    onSkipSignatures: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf(0) }

    // Inspector info
    var inspectorName by remember { mutableStateOf(initialInspectorName.ifBlank { "Field Engineer" }) }
    var inspectorRole by remember { mutableStateOf("Lead Site Surveyor") }
    val inspectorPadState = remember { SignaturePadState() }

    // Client info
    var clientName by remember { mutableStateOf("") }
    var clientCompany by remember { mutableStateOf(initialCompanyName.ifBlank { "Client Representative" }) }
    val clientPadState = remember { SignaturePadState() }

    // Approval status
    var approvalStatus by remember { mutableStateOf("VERIFIED & APPROVED") }
    val statusOptions = listOf(
        "VERIFIED & APPROVED",
        "CONDITIONAL PASS",
        "RECTIFICATION REQUIRED",
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .padding(vertical = 16.dp),
        icon = { Icon(Icons.Filled.Verified, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Inspection sign-off") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(
                        "Inspector" to Icons.Filled.Person,
                        "Client" to Icons.Filled.Handshake,
                        "Status" to Icons.Filled.TaskAlt,
                    ).forEachIndexed { i, (label, icon) ->
                        Tab(
                            selected = selectedTab == i,
                            onClick = { selectedTab = i },
                            text = { Text(label, maxLines = 1) },
                            icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                when (selectedTab) {
                    0 -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SignoffField("Inspector name", inspectorName, Icons.Filled.Person) { inspectorName = it }
                        SignoffField("Designation / title", inspectorRole, Icons.Filled.Badge) { inspectorRole = it }
                        SignatureHeader("Signature", inspectorPadState)
                        SignaturePad(
                            state = inspectorPadState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                        )
                    }
                    1 -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SignoffField("Client / witness name", clientName, Icons.Filled.Person) { clientName = it }
                        SignoffField("Company / organization", clientCompany, Icons.Filled.Business) { clientCompany = it }
                        SignatureHeader("Signature (optional)", clientPadState)
                        SignaturePad(
                            state = clientPadState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                        )
                    }
                    2 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Compliance status",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            statusOptions.forEachIndexed { i, status ->
                                StatusOption(
                                    label = status,
                                    icon = when (i) {
                                        0 -> Icons.Filled.CheckCircle
                                        1 -> Icons.Filled.Info
                                        else -> Icons.Filled.Warning
                                    },
                                    tint = when (i) {
                                        0 -> MaterialTheme.colorScheme.tertiary
                                        1 -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.error
                                    },
                                    selected = approvalStatus == status,
                                    onSelect = { approvalStatus = status },
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Names, signatures and this status are added to the PDF report together with each photo's location and time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val signoff = InspectionSignoff(
                        inspectorName = inspectorName,
                        inspectorRole = inspectorRole,
                        inspectorSignature = inspectorPadState.createBitmap(),
                        clientName = clientName,
                        clientCompany = clientCompany,
                        clientSignature = clientPadState.createBitmap(),
                        approvalStatus = approvalStatus,
                    )
                    onConfirm(signoff)
                },
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(Icons.Filled.Draw, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Sign & create PDF")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onSkipSignatures) {
                    Text("Skip signing")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
private fun SignoffField(label: String, value: String, icon: ImageVector, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SignatureHeader(label: String, state: SignaturePadState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = { state.undo() }, enabled = state.strokes.isNotEmpty()) {
            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
        }
        IconButton(onClick = { state.clear() }, enabled = state.hasSignature) {
            Icon(Icons.Filled.DeleteOutline, contentDescription = "Clear signature")
        }
    }
}

@Composable
private fun StatusOption(label: String, icon: ImageVector, tint: Color, selected: Boolean, onSelect: () -> Unit) {
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        RadioButton(selected = selected, onClick = null)
    }
}
