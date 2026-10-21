package com.geostamp.camera.audio

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

enum class VoiceRecorderState {
    IDLE,
    RECORDING,
    PLAYING
}

class VoiceMemoManager(private val context: Context, private val scope: CoroutineScope) {

    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var tickerJob: Job? = null
    private var currentRecordingFile: File? = null

    var state = VoiceRecorderState.IDLE
        private set

    /** Starts recording a high-quality AAC voice memo to the given target file. */
    fun startRecording(
        outputFile: File,
        onTick: (durationSec: Int, amplitude: Int) -> Unit,
        onError: (String) -> Unit
    ) {
        if (state != VoiceRecorderState.IDLE) {
            stopAll()
        }

        try {
            outputFile.parentFile?.mkdirs()
            currentRecordingFile = outputFile

            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mr.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            recorder = mr
            state = VoiceRecorderState.RECORDING

            var elapsedSeconds = 0
            tickerJob = scope.launch(Dispatchers.Main) {
                while (isActive && state == VoiceRecorderState.RECORDING) {
                    delay(500)
                    elapsedSeconds++
                    val amp = try {
                        recorder?.maxAmplitude ?: 0
                    } catch (e: Exception) {
                        0
                    }
                    onTick(elapsedSeconds / 2, amp)
                }
            }
        } catch (e: Exception) {
            Log.e("VoiceMemoManager", "Failed to start recording audio memo", e)
            stopAll()
            onError(e.localizedMessage ?: "Audio recording failed")
        }
    }

    /** Stops current recording and returns the final recorded file. */
    fun stopRecording(): File? {
        if (state != VoiceRecorderState.RECORDING) return null

        tickerJob?.cancel()
        tickerJob = null

        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.w("VoiceMemoManager", "Exception stopping MediaRecorder", e)
        } finally {
            recorder = null
            state = VoiceRecorderState.IDLE
        }
        return currentRecordingFile
    }

    /** Plays an existing audio memo file with real-time playback progress callback. */
    fun startPlayback(
        file: File,
        onProgress: (currentSec: Int, totalSec: Int) -> Unit,
        onComplete: () -> Unit
    ) {
        if (state != VoiceRecorderState.IDLE) {
            stopAll()
        }
        if (!file.exists() || file.length() == 0L) {
            onComplete()
            return
        }

        try {
            val mp = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
            }
            player = mp
            state = VoiceRecorderState.PLAYING

            val totalDurationSec = (mp.duration / 1000).coerceAtLeast(1)

            tickerJob = scope.launch(Dispatchers.Main) {
                while (isActive && state == VoiceRecorderState.PLAYING && mp.isPlaying) {
                    val currentSec = mp.currentPosition / 1000
                    onProgress(currentSec, totalDurationSec)
                    delay(250)
                }
            }

            mp.setOnCompletionListener {
                stopPlayback()
                onComplete()
            }
        } catch (e: Exception) {
            Log.e("VoiceMemoManager", "Failed to play voice memo", e)
            stopPlayback()
            onComplete()
        }
    }

    /** Stops active audio playback. */
    fun stopPlayback() {
        tickerJob?.cancel()
        tickerJob = null

        try {
            player?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (e: Exception) {
            Log.w("VoiceMemoManager", "Exception stopping MediaPlayer", e)
        } finally {
            player = null
            state = VoiceRecorderState.IDLE
        }
    }

    /** Releases all recorder and player resources. */
    fun stopAll() {
        stopRecording()
        stopPlayback()
    }

    /**
     * Starts listening to speech and returns transcribed text for hands-free field note entry.
     */
    fun startSpeechToText(
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition not available on this device")
            return
        }

        scope.launch(Dispatchers.Main) {
            try {
                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) {
                        onError("Speech recognition error code: $error")
                        recognizer.destroy()
                    }
                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        if (text.isNotBlank()) {
                            onResult(text)
                        }
                        recognizer.destroy()
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        matches?.firstOrNull()?.let { onResult(it) }
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                recognizer.startListening(intent)
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Failed to initialize speech recognizer")
            }
        }
    }
}
