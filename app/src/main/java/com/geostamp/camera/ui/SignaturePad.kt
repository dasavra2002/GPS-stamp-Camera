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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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

@Composable
fun SignaturePad(
    state: SignaturePadState,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFFF9FAFB),
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
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
                color = Color.LightGray.copy(alpha = 0.6f),
                start = Offset(24f, lineY),
                end = Offset(size.width - 24f, lineY),
                strokeWidth = 1.5f,
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

        // Watermark placeholder text when empty
        if (!state.hasSignature) {
            Text(
                text = "Sign here with your finger or stylus",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(8.dp)
            )
        }

        // Bottom right hint
        Text(
            text = "✕",
            style = MaterialTheme.typography.titleMedium,
            color = Color.LightGray.copy(alpha = 0.8f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 12.dp)
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
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Verified,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Official Inspection Sign-Off", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 0.dp,
                    containerColor = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("1. Inspector") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("2. Client / Witness") }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("3. Status & Audit") }
                    )
                }

                Spacer(Modifier.height(12.dp))

                when (selectedTab) {
                    0 -> {
                        // Inspector Tab
                        OutlinedTextField(
                            value = inspectorName,
                            onValueChange = { inspectorName = it },
                            label = { Text("Inspector Name") },
                            leadingIcon = { Icon(Icons.Filled.Person, null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = inspectorRole,
                            onValueChange = { inspectorRole = it },
                            label = { Text("Designation / Title") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Draw Signature", style = MaterialTheme.typography.labelLarge)
                            Row {
                                IconButton(
                                    onClick = { inspectorPadState.undo() },
                                    enabled = inspectorPadState.strokes.isNotEmpty()
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                                }
                                IconButton(
                                    onClick = { inspectorPadState.clear() },
                                    enabled = inspectorPadState.hasSignature
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Clear")
                                }
                            }
                        }
                        SignaturePad(
                            state = inspectorPadState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                        )
                    }
                    1 -> {
                        // Client Tab
                        OutlinedTextField(
                            value = clientName,
                            onValueChange = { clientName = it },
                            label = { Text("Client / Witness Name") },
                            leadingIcon = { Icon(Icons.Filled.Person, null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = clientCompany,
                            onValueChange = { clientCompany = it },
                            label = { Text("Company / Organization") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Draw Client Signature (Optional)", style = MaterialTheme.typography.labelLarge)
                            Row {
                                IconButton(
                                    onClick = { clientPadState.undo() },
                                    enabled = clientPadState.strokes.isNotEmpty()
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                                }
                                IconButton(
                                    onClick = { clientPadState.clear() },
                                    enabled = clientPadState.hasSignature
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Clear")
                                }
                            }
                        }
                        SignaturePad(
                            state = clientPadState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                        )
                    }
                    2 -> {
                        // Status & Audit Tab
                        Text("Compliance Status", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            statusOptions.forEach { status ->
                                FilterChip(
                                    selected = approvalStatus == status,
                                    onClick = { approvalStatus = status },
                                    label = { Text(status) },
                                    leadingIcon = if (approvalStatus == status) {
                                        { Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "A cryptographically hashed certificate seal (SHA-256) will be embedded automatically along with geolocation, timestamp, and audit metadata.",
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
                }
            ) {
                Icon(Icons.Filled.Draw, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Sign & Generate PDF")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onSkipSignatures) {
                    Text("Skip Signatures")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
