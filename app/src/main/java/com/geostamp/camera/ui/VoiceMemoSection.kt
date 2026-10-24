package com.geostamp.camera.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.geostamp.camera.audio.VoiceMemoManager
import com.geostamp.camera.audio.VoiceRecorderState
import com.geostamp.camera.ui.theme.CameraColors
import java.io.File
import java.util.Locale

@Composable
fun VoiceMemoSection(
    photoName: String,
    audioMemoPath: String?,
    onAudioRecorded: (filePath: String, durationSec: Int) -> Unit,
    onAudioDeleted: () -> Unit,
    onSpeechTranscribed: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val memoManager = remember { VoiceMemoManager(context, scope) }

    var isRecording by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var recordingDurationSec by remember { mutableIntStateOf(0) }
    var playbackCurrentSec by remember { mutableIntStateOf(0) }
    var playbackTotalSec by remember { mutableIntStateOf(0) }
    var isTranscribing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            memoManager.stopAll()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulsing_mic")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    fun formatDuration(sec: Int): String {
        val m = sec / 60
        val s = sec % 60
        return String.format(Locale.US, "%02d:%02d", m, s)
    }

    fun startRecording() {
        val sanitizedName = photoName.replace(Regex("[^a-zA-Z0-9_.-]"), "_")
        val audioFile = File(context.getExternalFilesDir("voice_memos"), "${sanitizedName}_memo.m4a")
        recordingDurationSec = 0
        isRecording = true
        errorMessage = null
        memoManager.startRecording(
            outputFile = audioFile,
            onTick = { sec, _ ->
                recordingDurationSec = sec
            },
            onError = { err ->
                isRecording = false
                errorMessage = err
            }
        )
    }

    fun startDictation() {
        if (isTranscribing) return
        isTranscribing = true
        errorMessage = null
        memoManager.startSpeechToText(
            onResult = { text ->
                isTranscribing = false
                onSpeechTranscribed(text)
            },
            onError = { err ->
                isTranscribing = false
                errorMessage = err
            }
        )
    }

    SectionCard(title = "Voice memo", icon = Icons.Filled.Mic, modifier = modifier) {
        if (isRecording) {
            // Currently recording
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f), MaterialTheme.shapes.medium)
                    .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .scale(pulseScale)
                            .background(CameraColors.Recording, CircleShape)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        formatDuration(recordingDurationSec),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Button(
                    onClick = {
                        val recordedFile = memoManager.stopRecording()
                        isRecording = false
                        if (recordedFile != null && recordedFile.exists() && recordedFile.length() > 0) {
                            onAudioRecorded(recordedFile.absolutePath, recordingDurationSec)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Stop")
                }
            }
        } else if (audioMemoPath != null) {
            // Saved audio memo exists
            val audioFile = File(audioMemoPath)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.medium)
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = {
                        if (isPlaying) {
                            memoManager.stopPlayback()
                            isPlaying = false
                        } else {
                            isPlaying = true
                            memoManager.startPlayback(
                                file = audioFile,
                                onProgress = { cur, tot ->
                                    playbackCurrentSec = cur
                                    playbackTotalSec = tot
                                },
                                onComplete = {
                                    isPlaying = false
                                    playbackCurrentSec = 0
                                }
                            )
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                ) {
                    val progress = if (playbackTotalSec > 0) {
                        (playbackCurrentSec.toFloat() / playbackTotalSec).coerceIn(0f, 1f)
                    } else 0f

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            formatDuration(playbackCurrentSec),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            if (playbackTotalSec > 0) formatDuration(playbackTotalSec) else "Memo attached",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = {
                        memoManager.stopAll()
                        isPlaying = false
                        try {
                            if (audioFile.exists()) audioFile.delete()
                        } catch (_: Exception) {}
                        onAudioDeleted()
                    }
                ) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = "Delete voice memo",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        if (!isRecording) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (audioMemoPath == null) {
                    OutlinedButton(
                        onClick = { startRecording() },
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Record memo", maxLines = 1)
                    }
                }
                FilledTonalButton(
                    onClick = { startDictation() },
                    enabled = !isTranscribing,
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isTranscribing) "Listening…" else "Dictate note", maxLines = 1)
                }
            }
            Text(
                "A memo is saved with the photo. Dictation adds the words to the stamp note.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(visible = errorMessage != null) {
            errorMessage?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
