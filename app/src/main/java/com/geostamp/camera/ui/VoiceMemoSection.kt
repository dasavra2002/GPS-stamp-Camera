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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.geostamp.camera.audio.VoiceMemoManager
import com.geostamp.camera.audio.VoiceRecorderState
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

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Voice Note / Field Memo",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                AssistChip(
                    onClick = {
                        if (!isTranscribing) {
                            isTranscribing = true
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
                    },
                    label = {
                        Text(if (isTranscribing) "Listening…" else "Dictate Note")
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.RecordVoiceOver,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isTranscribing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                )
            }

            Spacer(Modifier.height(10.dp))

            if (audioMemoPath == null && !isRecording) {
                // No recording present
                OutlinedButton(
                    onClick = {
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
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Record Audio Memo")
                }
            } else if (isRecording) {
                // Currently recording
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .scale(pulseScale)
                                .background(Color(0xFFE53935), CircleShape)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Recording: ${formatDuration(recordingDurationSec)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onErrorContainer
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
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = "Stop", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Finish")
                    }
                }
            } else if (audioMemoPath != null) {
                // Saved audio memo exists
                val audioFile = File(audioMemoPath)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
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
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
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
                        Spacer(Modifier.height(4.dp))
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
                                if (playbackTotalSec > 0) formatDuration(playbackTotalSec) else "Audio Attached",
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
                            Icons.Filled.Delete,
                            contentDescription = "Delete Audio Memo",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = errorMessage != null) {
                errorMessage?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }
}
