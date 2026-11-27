package com.geostamp.camera.camera

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * In-memory camera diagnostics (last [MAX] lines): binds, camera state changes, viewfinder state,
 * captures and errors. Users can copy it from Settings to report device-specific camera problems.
 */
object CameraLog {
    private const val MAX = 400
    private const val TAG = "GeoStampCam"
    private val lines = ArrayDeque<String>()
    private val time = DateTimeFormatter.ofPattern("HH:mm:ss.SSS", Locale.US)

    fun d(msg: String) {
        Log.d(TAG, msg)
        val line = time.format(LocalTime.now()) + " [" + Thread.currentThread().name + "] " + msg
        synchronized(lines) {
            lines.addLast(line)
            while (lines.size > MAX) lines.removeFirst()
        }
    }

    fun e(msg: String, t: Throwable?) {
        Log.w(TAG, msg, t)
        d("ERROR $msg: ${t?.javaClass?.simpleName}: ${t?.message}" + (t?.cause?.let { " (cause ${it.javaClass.simpleName}: ${it.message})" } ?: ""))
    }

    fun dump(appVersion: String? = null): String {
        val header = buildString {
            appendLine("GeoStamp camera log")
            appendLine("App: ${appVersion ?: "?"}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}), Android ${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT}")
            appendLine("Build: ${Build.FINGERPRINT}")
            appendLine("----")
        }
        return header + synchronized(lines) { lines.joinToString("\n") }
    }

    fun clear() = synchronized(lines) { lines.clear() }

    @Volatile private var monitorStarted = false

    /**
     * Logs when the main thread stops responding for over a second, with what it was doing,
     * so a "frozen app" report shows the exact call that blocked.
     */
    fun startFreezeMonitor() {
        if (monitorStarted) return
        monitorStarted = true
        val main = Handler(Looper.getMainLooper())
        Thread({
            var lastReported = 0L
            while (true) {
                val answered = java.util.concurrent.atomic.AtomicBoolean(false)
                val sent = SystemClock.elapsedRealtime()
                main.post { answered.set(true) }
                Thread.sleep(1_000)
                if (!answered.get()) {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastReported > 5_000) {
                        lastReported = now
                        val stack = Looper.getMainLooper().thread.stackTrace.take(25)
                            .joinToString("\n    at ") { it.toString() }
                        d("FREEZE main thread blocked >${now - sent} ms\n    at $stack")
                    }
                    while (!answered.get()) Thread.sleep(100)
                    d("main thread responsive again after ${SystemClock.elapsedRealtime() - sent} ms")
                }
            }
        }, "freeze-monitor").apply { isDaemon = true }.start()
    }
}
