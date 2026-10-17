package com.geostamp.camera.stamp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.geostamp.camera.data.MapPosition
import com.geostamp.camera.data.MapType
import com.geostamp.camera.data.StampBackground
import com.geostamp.camera.data.StampConfig
import com.geostamp.camera.data.StampData
import com.geostamp.camera.data.StampField
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Draws the stamp (map thumbnail + info panel) into a transparent bitmap.
 * The same renderer is used for the live preview, camera photos and gallery photos,
 * so what you see on screen is what gets saved.
 */
object WatermarkRenderer {

    private const val LABEL_AMBER = 0xFFFFC107.toInt()

    private data class Line(val label: String, val value: String, val title: Boolean = false)

    fun flagEmoji(countryCode: String?): String {
        val cc = countryCode?.uppercase(Locale.US)?.takeIf { it.length == 2 && it.all { c -> c in 'A'..'Z' } }
            ?: return ""
        val sb = StringBuilder()
        cc.forEach { sb.appendCodePoint(0x1F1E6 + (it - 'A')) }
        return sb.toString()
    }

    fun direction(deg: Float): String {
        val names = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        return names[(((deg % 360f) + 360f) % 360f / 45f).roundToInt() % 8]
    }

    fun formatDate(config: StampConfig, time: ZonedDateTime): String {
        val parts = mutableListOf<String>()
        if (config.has(StampField.DATE_TIME)) {
            parts += DateTimeFormatter.ofPattern(config.dateFormat.pattern, Locale.getDefault()).format(time)
        }
        if (config.has(StampField.TIME_ZONE)) {
            parts += "GMT " + DateTimeFormatter.ofPattern("xxx", Locale.US).format(time)
        }
        return parts.joinToString(" ")
    }

    private fun buildLines(config: StampConfig, d: StampData, time: ZonedDateTime, cameraLabel: String?, preview: Boolean): List<Line> {
        val lines = mutableListOf<Line>()
        val flag = if (config.has(StampField.COUNTRY_FLAG)) flagEmoji(d.countryCode) else ""
        if (config.has(StampField.TITLE)) {
            // "Locating…" is only a hint on screen; a saved photo never says that.
            val t = listOf(d.title ?: if (preview) "Locating…" else "-", flag).filter { it.isNotBlank() }.joinToString(" ")
            lines += Line("", t, title = true)
        } else if (flag.isNotEmpty()) {
            lines += Line("", flag, title = true)
        }
        if (config.has(StampField.SHORT_ADDRESS)) lines += Line("", d.shortAddress ?: "-")
        if (config.has(StampField.ADDRESS)) lines += Line("", d.address ?: "-")
        if (config.has(StampField.LAT_LONG)) {
            val v = if (d.latitude != null && d.longitude != null) {
                String.format(Locale.US, "Lat %.6f° Long %.6f°", d.latitude, d.longitude)
            } else "Lat - Long -"
            lines += Line("", v)
        }
        val altAcc = mutableListOf<String>()
        if (config.has(StampField.ALTITUDE)) {
            altAcc += "Altitude " + (d.altitude?.let { String.format(Locale.US, "%.0f m", it) } ?: "-")
        }
        if (config.has(StampField.ACCURACY)) {
            altAcc += "Accuracy " + (d.accuracyM?.let { String.format(Locale.US, "±%.0f m", it) } ?: "-")
        }
        if (altAcc.isNotEmpty()) lines += Line("", altAcc.joinToString("   "))
        if (config.has(StampField.PLUS_CODE)) lines += Line("Plus Code : ", d.plusCode ?: "-")
        val date = formatDate(config, time)
        if (date.isNotEmpty()) lines += Line("", date)
        if (config.has(StampField.NOTE) && config.note.isNotBlank()) lines += Line("", config.note)
        if (config.has(StampField.NUMBERING)) lines += Line("", config.number.toString())
        if (config.has(StampField.PERSON_NAME)) lines += Line("Person Name : ", config.personName.ifBlank { "-" })
        if (config.has(StampField.PHONE)) lines += Line("☎ ", config.phone.ifBlank { "-" })
        if (config.has(StampField.CAMERA_INFO) && !cameraLabel.isNullOrBlank()) lines += Line("Camera : ", cameraLabel)
        return lines
    }

    private fun buildChips(config: StampConfig, d: StampData): List<String> {
        val chips = mutableListOf<String>()
        if (config.has(StampField.TEMPERATURE)) {
            chips += "☀ " + (d.temperatureC?.let { String.format(Locale.US, "%.2f° C", it) } ?: "-")
        }
        if (config.has(StampField.WIND)) {
            chips += "💨 " + (d.windKmh?.let { String.format(Locale.US, "%.2f km/h", it) } ?: "-")
        }
        if (config.has(StampField.HUMIDITY)) chips += "💧 " + (d.humidity?.let { "$it%" } ?: "-")
        if (config.has(StampField.PRESSURE)) {
            chips += "⏲ " + (d.pressureHpa?.let { String.format(Locale.US, "%.0f hpa", it) } ?: "-")
        }
        if (config.has(StampField.COMPASS)) {
            chips += "🧭 " + (d.azimuthDeg?.let { String.format(Locale.US, "%.0f° %s", it, direction(it)) } ?: "-")
        }
        if (config.has(StampField.MAGNETIC)) {
            chips += "🧲 " + (d.magneticUt?.let { String.format(Locale.US, "%.2f µT", it) } ?: "-")
        }
        if (config.has(StampField.SOUND_LEVEL)) {
            chips += "🔊 " + (d.soundDb?.let { String.format(Locale.US, "%.1f dB", it) } ?: "-")
        }
        return chips
    }

    /** Word-wraps [text] to [maxWidth]. Very long words are broken by character. */
    private fun wrap(paint: Paint, text: String, maxWidth: Float): List<String> {
        if (paint.measureText(text) <= maxWidth) return listOf(text)
        val out = mutableListOf<String>()
        var current = ""
        for (word in text.split(" ")) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current = candidate
            } else {
                if (current.isNotEmpty()) out += current
                var w = word
                while (paint.measureText(w) > maxWidth && w.length > 1) {
                    val n = paint.breakText(w, true, maxWidth, null).coerceAtLeast(1)
                    out += w.substring(0, n)
                    w = w.substring(n)
                }
                current = w
            }
        }
        if (current.isNotEmpty()) out += current
        return out
    }

    /**
     * @param widthPx width of the whole stamp (map + panel).
     * @param preview true for the on-screen preview (may show "Locating…" style hints).
     * @return the stamp bitmap, or null when every item is switched off.
     */
    fun render(
        widthPx: Int,
        config: StampConfig,
        data: StampData,
        time: ZonedDateTime,
        cameraLabel: String? = null,
        preview: Boolean = false,
    ): Bitmap? {
        val lines = buildLines(config, data, time, cameraLabel, preview)
        val chips = buildChips(config, data)
        val showMap = config.has(StampField.MAP)
        val logo = if (config.has(StampField.LOGO)) config.logo else null
        if (lines.isEmpty() && chips.isEmpty() && !showMap && logo == null) return null

        val w = widthPx.toFloat()
        val u = w / 100f
        val pad = 1.8f * u
        val gap = 1.2f * u
        val mapSize = if (showMap) 30f * u * config.mapScalePercent / 100f else 0f
        val panelLeftOffset = if (showMap) mapSize + gap else 0f
        val panelWidth = w - panelLeftOffset
        val logoSize = if (logo != null) 7.5f * u else 0f

        // Colours
        val lightBg = config.background == StampBackground.LIGHT
        val textColor = when {
            lightBg && (config.color.multi || config.color.text == Color.WHITE) -> Color.BLACK
            else -> config.color.text
        }
        val labelColor = if (config.color.multi) (if (lightBg) 0xFFB26A00.toInt() else LABEL_AMBER) else textColor

        val base = Typeface.create(config.font.family, if (config.font.bold) Typeface.BOLD else Typeface.NORMAL)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(base, Typeface.BOLD); textSize = 3.6f * u; color = textColor
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = base; textSize = 2.5f * u; color = textColor }
        val labelPaint = Paint(bodyPaint).apply { color = labelColor }
        val chipPaint = Paint(bodyPaint).apply { textSize = 2.4f * u }
        if (config.background == StampBackground.NONE) {
            listOf(titlePaint, bodyPaint, labelPaint, chipPaint).forEach { it.setShadowLayer(0.4f * u, 0f, 0.15f * u, Color.BLACK) }
        }

        // ---- measure text ----
        val textMaxWidth = panelWidth - 2 * pad - (if (logo != null) logoSize + gap else 0f)
        data class Row(val label: String, val text: String, val paint: Paint, val height: Float)
        val rows = mutableListOf<Row>()
        for (line in lines) {
            val p = if (line.title) titlePaint else bodyPaint
            val wrapped = wrap(p, line.label + line.value, textMaxWidth)
            wrapped.forEachIndexed { i, t ->
                val label = if (i == 0 && line.label.isNotEmpty() && t.startsWith(line.label)) line.label else ""
                rows += Row(label, t.removePrefix(label), p, p.textSize * 1.3f)
            }
        }
        // chips flow into rows
        val chipRows = mutableListOf<MutableList<String>>()
        val chipGap = 3f * u
        val chipAreaWidth = panelWidth - 2 * pad
        var rowWidth = 0f
        for (c in chips) {
            val cw = chipPaint.measureText(c)
            if (chipRows.isEmpty() || rowWidth + cw > chipAreaWidth) {
                chipRows += mutableListOf(c)
                rowWidth = cw + chipGap
            } else {
                chipRows.last() += c
                rowWidth += cw + chipGap
            }
        }
        val chipRowH = chipPaint.textSize * 1.6f
        val textHeight = rows.sumOf { it.height.toDouble() }.toFloat()
        val chipsHeight = chipRows.size * chipRowH + (if (chipRows.isNotEmpty() && rows.isNotEmpty()) 0.8f * u else 0f)
        val panelContent = max(textHeight + chipsHeight, logoSize)
        val hasPanel = rows.isNotEmpty() || chipRows.isNotEmpty() || logo != null
        val panelHeight = if (hasPanel) panelContent + 2 * pad else 0f
        val totalH = ceil(max(panelHeight, mapSize)).toInt().coerceAtLeast(1)

        val bmp = Bitmap.createBitmap(widthPx, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // ---- placement (map left or right) ----
        val mapLeft = if (config.mapPosition == MapPosition.LEFT) 0f else w - mapSize
        val panelLeft = if (config.mapPosition == MapPosition.LEFT) panelLeftOffset else 0f
        val panelTop = totalH - panelHeight
        val mapTop = totalH - mapSize

        // ---- map ----
        if (showMap) drawMap(canvas, RectF(mapLeft, mapTop, mapLeft + mapSize, mapTop + mapSize), data, u, preview)

        if (!hasPanel) return bmp

        // ---- panel ----
        val panelRect = RectF(panelLeft, panelTop, panelLeft + panelWidth, totalH.toFloat())
        if (config.background != StampBackground.NONE) {
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = config.background.color }
            canvas.drawRoundRect(panelRect, 1.6f * u, 1.6f * u, bg)
        }

        if (logo != null) {
            val l = panelRect.right - pad - logoSize
            val t = panelRect.top + pad
            val scale = minOf(logoSize / logo.width, logoSize / logo.height)
            val lw = logo.width * scale
            val lh = logo.height * scale
            val dst = RectF(l + (logoSize - lw) / 2, t + (logoSize - lh) / 2, l + (logoSize + lw) / 2, t + (logoSize + lh) / 2)
            canvas.drawBitmap(logo, null, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }

        var y = panelRect.top + pad
        val x = panelRect.left + pad
        for (row in rows) {
            val baseline = y + row.paint.textSize
            var cx = x
            if (row.label.isNotEmpty()) {
                labelPaint.textSize = row.paint.textSize
                canvas.drawText(row.label, cx, baseline, labelPaint)
                cx += labelPaint.measureText(row.label)
            }
            canvas.drawText(row.text, cx, baseline, row.paint)
            y += row.height
        }
        if (chipRows.isNotEmpty()) {
            if (rows.isNotEmpty()) y += 0.8f * u
            for (chipRow in chipRows) {
                var cx = x
                val baseline = y + chipPaint.textSize * 1.2f
                for (c in chipRow) {
                    canvas.drawText(c, cx, baseline, chipPaint)
                    cx += chipPaint.measureText(c) + chipGap
                }
                y += chipRowH
            }
        }
        return bmp
    }

    private fun drawMap(canvas: Canvas, rect: RectF, data: StampData, u: Float, preview: Boolean) {
        val radius = 1.6f * u
        val clip = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)
        val map = data.mapBitmap
        if (map != null) {
            canvas.drawBitmap(map, Rect(0, 0, map.width, map.height), rect, Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            canvas.drawColor(0xFF4A4A4A.toInt())
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.LTGRAY; textSize = 2.2f * u; textAlign = Paint.Align.CENTER
            }
            val msg = when {
                data.latitude == null -> "No location"
                preview -> "Loading map…"
                else -> "Map unavailable"
            }
            canvas.drawText(msg, rect.centerX(), rect.bottom - 2.5f * u, p)
        }

        // Pin in the centre
        val cx = rect.centerX()
        val cy = rect.centerY()
        val r = rect.width() * 0.07f
        val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE53935.toInt() }
        val pin = Path().apply {
            moveTo(cx, cy)
            lineTo(cx - r * 0.85f, cy - r * 1.4f)
            lineTo(cx + r * 0.85f, cy - r * 1.4f)
            close()
        }
        canvas.drawPath(pin, pinPaint)
        canvas.drawCircle(cx, cy - r * 1.9f, r, pinPaint)
        canvas.drawCircle(cx, cy - r * 1.9f, r * 0.4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF7F0000.toInt() })

        // Required attribution for the free map tiles
        if (map != null) {
            val attr = when (data.mapType) {
                MapType.STREET -> "© OpenStreetMap"
                else -> "© Esri"
            }
            val ap = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; textSize = 1.5f * u; textAlign = Paint.Align.RIGHT
                setShadowLayer(0.3f * u, 0f, 0f, Color.BLACK)
            }
            canvas.drawText(attr, rect.right - 0.8f * u, rect.bottom - 0.8f * u, ap)
        }
        canvas.restore()
    }
}
