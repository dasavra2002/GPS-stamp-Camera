package com.geostamp.camera

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/** Installs the crash catcher as early as possible. */
class GeoStampApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }
}

/**
 * Saves the stack trace of any crash, so the next launch can show it with a Share button.
 * Built with plain Android views, so it still works if the crash came from the Compose UI.
 */
object CrashReporter {
    private fun file(context: Context) = File(context.filesDir, "last_crash.txt")

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                val info = buildString {
                    append("GeoStamp Camera ").append(BuildConfigInfo.version(app)).append('\n')
                    append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                    append(" (Android ").append(Build.VERSION.RELEASE).append(", API ").append(Build.VERSION.SDK_INT).append(")\n")
                    append("Thread: ").append(thread.name).append("\n\n")
                    append(sw.toString())
                }
                file(app).writeText(info)
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Shows the saved crash report, if any. Returns true when the report screen is shown. */
    fun showIfCrashed(activity: Activity, onContinue: () -> Unit): Boolean {
        val f = file(activity)
        if (!f.exists()) return false
        val report = runCatching { f.readText() }.getOrDefault("(could not read the report)")

        val pad = (16 * activity.resources.displayMetrics.density).toInt()
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(activity).apply {
            text = "GeoStamp Camera crashed last time.\nTap Share to send this report to the developer."
            setTextColor(Color.WHITE)
            textSize = 16f
        })
        val buttons = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        buttons.addView(Button(activity).apply {
            text = "Share"
            setOnClickListener {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "GeoStamp Camera crash report")
                    putExtra(Intent.EXTRA_TEXT, report)
                }
                activity.startActivity(Intent.createChooser(send, "Share crash report"))
            }
        })
        buttons.addView(Button(activity).apply {
            text = "Continue"
            setOnClickListener {
                f.delete()
                onContinue()
            }
        })
        root.addView(buttons)
        val scroll = ScrollView(activity)
        scroll.addView(TextView(activity).apply {
            text = report
            setTextColor(Color.LTGRAY)
            textSize = 11f
            setTextIsSelectable(true)
        })
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        activity.setContentView(root)
        return true
    }
}

private object BuildConfigInfo {
    fun version(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    }.getOrDefault("")
}
