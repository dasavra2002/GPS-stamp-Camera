package com.geostamp.camera.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.geostamp.camera.MainActivity
import com.geostamp.camera.R
import com.geostamp.camera.data.StampSettings

class GeoStampAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val settings = StampSettings(context.applicationContext)
        val siteName = settings.siteName.trim()

        val siteStatus = if (siteName.isNotEmpty()) {
            "📍 Site: $siteName"
        } else {
            "📍 Ready to stamp"
        }

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_geostamp)
            views.setTextViewText(R.id.widget_site_status, siteStatus)

            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            // 1. Regular Camera Intent
            val cameraIntent = Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_OPEN_CAMERA
                setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val cameraPendingIntent = PendingIntent.getActivity(context, 101, cameraIntent, pendingIntentFlags)
            views.setOnClickPendingIntent(R.id.widget_btn_camera, cameraPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_root, cameraPendingIntent)

            // 2. Punch In Intent
            val punchIntent = Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_PUNCH_IN
                putExtra(MainActivity.EXTRA_START_MODE, "ATTENDANCE")
                setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val punchPendingIntent = PendingIntent.getActivity(context, 102, punchIntent, pendingIntentFlags)
            views.setOnClickPendingIntent(R.id.widget_btn_punch, punchPendingIntent)

            // 3. Reports / Gallery Intent
            val galleryIntent = Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_OPEN_GALLERY
                putExtra(MainActivity.EXTRA_START_SCREEN, "GALLERY")
                setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val galleryPendingIntent = PendingIntent.getActivity(context, 103, galleryIntent, pendingIntentFlags)
            views.setOnClickPendingIntent(R.id.widget_btn_gallery, galleryPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, GeoStampAppWidgetProvider::class.java)
            val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            if (allWidgetIds != null && allWidgetIds.isNotEmpty()) {
                val intent = Intent(context, GeoStampAppWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, allWidgetIds)
                }
                context.sendBroadcast(intent)
            }
        }
    }
}
