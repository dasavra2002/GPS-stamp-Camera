package com.geostamp.camera.report

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.geostamp.camera.stamp.PhotoComposer
import com.geostamp.camera.ui.GalleryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object ReportGenerator {

    /** Generates a formatted PDF inspection report for the given gallery photos. Returns its Uri. */
    suspend fun generatePdfReport(
        context: Context,
        items: List<GalleryItem>,
        projectName: String = "Site Inspection",
        inspectorName: String = "",
    ): Uri? = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext null

        val pdfDoc = PdfDocument()
        val pageW = 595 // A4 standard width in points
        val pageH = 842 // A4 standard height in points
        val margin = 36f

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.BLACK
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10f
            color = Color.DKGRAY
        }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = 0xFF1976D2.toInt()
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9f
            color = Color.BLACK
        }
        val boldBody = Paint(bodyPaint).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        }
        val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF8F9FA.toInt()
        }

        // Each page holds up to 2 inspection items
        val itemsPerPage = 2
        val totalPages = ((items.size + itemsPerPage - 1) / itemsPerPage).coerceAtLeast(1)

        var itemIdx = 0
        for (pageNum in 1..totalPages) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create()
            val page = pdfDoc.startPage(pageInfo)
            val canvas = page.canvas

            // Page Header
            var y = margin + 15f
            if (pageNum == 1) {
                canvas.drawText("GeoStamp Field Inspection Report", margin, y, titlePaint)
                y += 18f
                val subtitle = buildString {
                    append("Project / Site: ").append(projectName.ifBlank { "General Field Visit" })
                    if (inspectorName.isNotBlank()) append("   |   Inspector: ").append(inspectorName)
                    append("   |   Generated: ").append(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(ZonedDateTime.now()))
                }
                canvas.drawText(subtitle, margin, y, subPaint)
                y += 10f
                canvas.drawLine(margin, y, pageW - margin, y, linePaint)
                y += 15f
            } else {
                canvas.drawText("$projectName - Page $pageNum of $totalPages", margin, y, subPaint)
                y += 10f
                canvas.drawLine(margin, y, pageW - margin, y, linePaint)
                y += 15f
            }

            // Draw items for this page
            val pageItems = items.drop(itemIdx).take(itemsPerPage)
            itemIdx += pageItems.size

            val itemCardH = 340f
            for (item in pageItems) {
                val cardTop = y
                val cardBottom = cardTop + itemCardH
                val cardRect = RectF(margin, cardTop, pageW - margin, cardBottom)
                canvas.drawRoundRect(cardRect, 8f, 8f, cardBgPaint)

                // Decode photo thumbnail
                val thumbBmp = try {
                    PhotoComposer.thumbnail(context, item.uri, maxSide = 360)
                } catch (e: Exception) {
                    null
                }

                // Draw photo thumbnail
                val photoW = 200f
                val photoH = 260f
                val photoLeft = margin + 12f
                val photoTop = cardTop + 14f
                if (thumbBmp != null) {
                    val scale = minOf(photoW / thumbBmp.width, photoH / thumbBmp.height)
                    val dw = thumbBmp.width * scale
                    val dh = thumbBmp.height * scale
                    val dst = RectF(photoLeft + (photoW - dw) / 2, photoTop + (photoH - dh) / 2, photoLeft + (photoW + dw) / 2, photoTop + (photoH + dh) / 2)
                    canvas.drawBitmap(thumbBmp, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
                    thumbBmp.recycle()
                } else {
                    canvas.drawRect(photoLeft, photoTop, photoLeft + photoW, photoTop + photoH, linePaint)
                }

                // Info columns on right
                var textX = photoLeft + photoW + 16f
                var textY = photoTop + 14f

                canvas.drawText(item.name, textX, textY, headerPaint)
                textY += 16f

                val d = item.data
                val coords = if (d.latitude != null && d.longitude != null) {
                    String.format(Locale.US, "Lat %.6f°, Long %.6f°", d.latitude, d.longitude)
                } else "Location: Not Available"
                canvas.drawText("Coordinates:", textX, textY, boldBody)
                canvas.drawText(coords, textX + 65f, textY, bodyPaint)
                textY += 14f

                if (d.plusCode != null) {
                    canvas.drawText("Plus Code:", textX, textY, boldBody)
                    canvas.drawText(d.plusCode, textX + 65f, textY, bodyPaint)
                    textY += 14f
                }

                if (d.altitude != null) {
                    canvas.drawText("Altitude:", textX, textY, boldBody)
                    canvas.drawText(String.format(Locale.US, "%.1f m", d.altitude), textX + 65f, textY, bodyPaint)
                    textY += 14f
                }

                val dt = DateTimeFormatter.ofPattern("EEEE, dd MMM yyyy, hh:mm:ss a", Locale.getDefault()).format(item.time)
                canvas.drawText("Captured:", textX, textY, boldBody)
                canvas.drawText(dt, textX + 65f, textY, bodyPaint)
                textY += 14f

                if (!d.address.isNullOrBlank()) {
                    canvas.drawText("Address:", textX, textY, boldBody)
                    textY += 12f
                    // Simple wrap
                    val words = d.address.split(" ")
                    var line = ""
                    for (word in words) {
                        if (bodyPaint.measureText("$line $word") < (pageW - margin - textX - 10f)) {
                            line = if (line.isEmpty()) word else "$line $word"
                        } else {
                            canvas.drawText(line, textX, textY, bodyPaint)
                            textY += 12f
                            line = word
                        }
                    }
                    if (line.isNotEmpty()) {
                        canvas.drawText(line, textX, textY, bodyPaint)
                        textY += 14f
                    }
                }

                val weatherList = mutableListOf<String>()
                d.temperatureC?.let { weatherList += String.format(Locale.US, "%.1f°C", it) }
                d.humidity?.let { weatherList += "$it% RH" }
                d.windKmh?.let { weatherList += String.format(Locale.US, "%.1f km/h", it) }
                if (weatherList.isNotEmpty()) {
                    canvas.drawText("Weather:", textX, textY, boldBody)
                    canvas.drawText(weatherList.joinToString("  •  "), textX + 65f, textY, bodyPaint)
                    textY += 14f
                }

                // Mini map
                val mapBmp = d.mapBitmap
                if (mapBmp != null) {
                    val mapSize = 75f
                    val mapDst = RectF(textX, textY, textX + mapSize, textY + mapSize)
                    canvas.drawBitmap(mapBmp, null, mapDst, Paint(Paint.FILTER_BITMAP_FLAG))
                }

                y = cardBottom + 16f
            }

            // Footer
            canvas.drawText("Page $pageNum of $totalPages", pageW / 2f - 20f, pageH - margin / 2f, subPaint)

            pdfDoc.finishPage(page)
        }

        val timeStr = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.US).format(ZonedDateTime.now())
        val fileName = "GeoStamp_Report_${timeStr}.pdf"

        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { pdfDoc.writeTo(it) }
        pdfDoc.close()

        savePdfToStorage(context, file, fileName)
    }

    /** Exports photos summary as a CSV file compatible with Excel and Google Sheets. */
    suspend fun generateCsvSummary(
        context: Context,
        items: List<GalleryItem>,
    ): Uri? = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext null

        val csv = buildString {
            append("Photo Name,Latitude,Longitude,Altitude (m),Plus Code,Timestamp,Address,Title\n")
            for (item in items) {
                val d = item.data
                val dt = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(item.time)
                fun escape(s: String?) = "\"" + (s ?: "").replace("\"", "\"\"") + "\""
                append(escape(item.name)).append(',')
                append(d.latitude?.let { String.format(Locale.US, "%.6f", it) } ?: "").append(',')
                append(d.longitude?.let { String.format(Locale.US, "%.6f", it) } ?: "").append(',')
                append(d.altitude?.let { String.format(Locale.US, "%.1f", it) } ?: "").append(',')
                append(escape(d.plusCode)).append(',')
                append(escape(dt)).append(',')
                append(escape(d.address)).append(',')
                append(escape(d.title)).append('\n')
            }
        }

        val timeStr = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.US).format(ZonedDateTime.now())
        val fileName = "GeoStamp_Export_${timeStr}.csv"
        val temp = File(context.cacheDir, fileName)
        temp.writeText(csv)

        saveFileToStorage(context, temp, fileName, "text/csv")
    }

    /** Exports photos as a Google Earth KML file for interactive 3D map exploration. */
    suspend fun generateKmlExport(
        context: Context,
        items: List<GalleryItem>,
        docName: String = "GeoStamp Photos Map",
    ): Uri? = withContext(Dispatchers.IO) {
        val geoItems = items.filter { it.data.latitude != null && it.data.longitude != null }
        if (geoItems.isEmpty()) return@withContext null

        val kml = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n")
            append("  <Document>\n")
            append("    <name>").append(docName).append("</name>\n")
            for (item in geoItems) {
                val d = item.data
                val dt = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(item.time)
                append("    <Placemark>\n")
                append("      <name>").append(item.name).append("</name>\n")
                append("      <description><![CDATA[\n")
                append("        <p><b>Time:</b> ").append(dt).append("</p>\n")
                d.address?.let { append("        <p><b>Address:</b> ").append(it).append("</p>\n") }
                d.plusCode?.let { append("        <p><b>Plus Code:</b> ").append(it).append("</p>\n") }
                d.altitude?.let { append("        <p><b>Altitude:</b> ").append(String.format(Locale.US, "%.1f m", it)).append("</p>\n") }
                append("      ]]></description>\n")
                append("      <Point>\n")
                append("        <coordinates>")
                append(String.format(Locale.US, "%.6f,%.6f", d.longitude, d.latitude))
                d.altitude?.let { append(String.format(Locale.US, ",%.1f", it)) }
                append("</coordinates>\n")
                append("      </Point>\n")
                append("    </Placemark>\n")
            }
            append("  </Document>\n")
            append("</kml>\n")
        }

        val timeStr = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.US).format(ZonedDateTime.now())
        val fileName = "GeoStamp_Map_${timeStr}.kml"
        val temp = File(context.cacheDir, fileName)
        temp.writeText(kml)

        saveFileToStorage(context, temp, fileName, "application/vnd.google-earth.kml+xml")
    }

    private fun savePdfToStorage(context: Context, temp: File, name: String): Uri? {
        return saveFileToStorage(context, temp, name, "application/pdf")
    }

    private fun saveFileToStorage(context: Context, temp: File, name: String, mimeType: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/GeoStamp_Reports")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val cr = context.contentResolver
        val uri = cr.insert(MediaStore.Files.getContentUri("external"), values) ?: return null

        return try {
            cr.openOutputStream(uri)?.use { out -> temp.inputStream().use { it.copyTo(out) } }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                cr.update(uri, values, null, null)
            }
            uri
        } catch (e: Exception) {
            cr.delete(uri, null, null)
            null
        } finally {
            temp.delete()
        }
    }
}
