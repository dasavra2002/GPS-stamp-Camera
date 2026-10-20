package com.geostamp.camera.stamp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.geostamp.camera.data.CoordinateConverter
import com.geostamp.camera.data.FrameDataDensity
import com.geostamp.camera.data.StampConfig
import com.geostamp.camera.data.StampData
import com.geostamp.camera.data.StampField
import com.geostamp.camera.data.WatermarkStyle
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Modern photography watermark & frame rendering engine.
 * Supports 12 distinct photo frame styles (Studio White, Yellow Block, Film Strips,
 * Luxury Matte, Exhibition Mats, Polaroid, Editorial Typography).
 *
 * Supports two density modes:
 *  - [FrameDataDensity.MINIMAL]: Minimalist photography layout (Brand, EXIF, Date, basic location).
 *  - [FrameDataDensity.FULL]: Complete data parity with OG watermark (Weather, Sensors, Site, Person,
 *    full Address, Plus code, Mini-map, Tamper-proof QR code).
 */
object FrameWatermarkRenderer {

    private const val ACCENT_YELLOW = 0xFFFFC107.toInt()
    private const val DARK_BG = 0xFF141416.toInt()
    private const val FILM_DARK = 0xFF18181A.toInt()
    private const val SPROCKET_COLOR = 0xFF0A0A0C.toInt()
    private const val SPROCKET_BORDER = 0xFF28282C.toInt()

    fun render(
        photo: Bitmap,
        config: StampConfig,
        data: StampData,
        time: ZonedDateTime,
        params: ShotParams?,
        cameraLabel: String? = null,
    ): Bitmap {
        val shot = params ?: ShotParams()
        return when (config.watermarkStyle) {
            WatermarkStyle.STUDIO_WHITE_BAR -> renderBottomBar(photo, config, data, time, shot, cameraLabel, isDark = false, hasYellowBlock = false, hasStripe = false, hasAvatar = false)
            WatermarkStyle.ACCENT_BLOCK_BAR -> renderBottomBar(photo, config, data, time, shot, cameraLabel, isDark = true, hasYellowBlock = true, hasStripe = false, hasAvatar = false)
            WatermarkStyle.ACCENT_STRIPE_BAR -> renderBottomBar(photo, config, data, time, shot, cameraLabel, isDark = true, hasYellowBlock = false, hasStripe = true, hasAvatar = false)
            WatermarkStyle.AVATAR_WHITE_BAR -> renderBottomBar(photo, config, data, time, shot, cameraLabel, isDark = false, hasYellowBlock = false, hasStripe = false, hasAvatar = true)
            WatermarkStyle.EXHIBITION_WHITE_MAT -> renderFullMatFrame(photo, config, data, time, shot, cameraLabel, bgColor = Color.WHITE, textColor = Color.BLACK, hasAvatar = config.showAvatarOnFrame)
            WatermarkStyle.FILM_STRIP_35MM -> render35mmFilmStrip(photo, config, data, time, shot, cameraLabel)
            WatermarkStyle.SIGNATURE_YELLOW_MAT -> renderSignatureYellowMat(photo, config, data, time, shot, cameraLabel)
            WatermarkStyle.LUXURY_BLACK_MAT -> renderLuxuryBlackMat(photo, config, data, time, shot, cameraLabel)
            WatermarkStyle.RETRO_VINTAGE_FILM -> renderRetroVintageFilm(photo, config, data, time, shot, cameraLabel)
            WatermarkStyle.EDITORIAL_TYPOGRAPHY -> renderEditorialTypography(photo, config, data, time, shot, cameraLabel)
            WatermarkStyle.POLAROID_SIDEBAR -> renderPolaroidSidebar(photo, config, data, time, shot, cameraLabel)
            WatermarkStyle.CLASSIC_GEO_BADGE -> photo
        }
    }

    fun renderPreview(
        previewWidth: Int,
        frameHeight: Int,
        config: StampConfig,
        data: StampData,
        time: ZonedDateTime,
        params: ShotParams?,
        cameraLabel: String? = null,
    ): Bitmap? {
        val shot = params ?: ShotParams()
        val dummy = Bitmap.createBitmap(previewWidth, frameHeight, Bitmap.Config.ARGB_8888)
        val framed = render(dummy, config, data, time, shot, cameraLabel)
        if (dummy !== framed) dummy.recycle()
        return framed
    }

    // --------------------------------------------------------------------------------------
    // 1. Bottom Bar Styles: Studio White, Yellow Block, Yellow Stripe, Avatar White
    // --------------------------------------------------------------------------------------

    private fun renderBottomBar(
        photo: Bitmap,
        config: StampConfig,
        data: StampData,
        time: ZonedDateTime,
        shot: ShotParams,
        cameraLabel: String?,
        isDark: Boolean,
        hasYellowBlock: Boolean,
        hasStripe: Boolean,
        hasAvatar: Boolean,
    ): Bitmap {
        val w = photo.width
        val isFull = config.frameDataDensity == FrameDataDensity.FULL
        val barH = (w * if (isFull) 0.185f else 0.115f).roundToInt().coerceIn(120, 750)
        val outH = photo.height + barH
        val out = Bitmap.createBitmap(w, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        canvas.drawBitmap(photo, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))

        val barRect = RectF(0f, photo.height.toFloat(), w.toFloat(), outH.toFloat())
        val bgPaint = Paint().apply {
            color = if (isDark) DARK_BG else Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawRect(barRect, bgPaint)

        if (hasStripe) {
            val stripePaint = Paint().apply { color = ACCENT_YELLOW; style = Paint.Style.FILL }
            val stripeH = (barH * 0.04f).coerceAtLeast(4f)
            canvas.drawRect(0f, photo.height.toFloat(), w.toFloat(), photo.height + stripeH, stripePaint)
        }

        val padX = w * 0.038f
        val centerY = photo.height + barH * 0.5f
        val textColor = if (isDark) Color.WHITE else Color.BLACK
        val subColor = if (isDark) 0xFFB0B0B0.toInt() else 0xFF666666.toInt()

        val brand = config.deviceBrand.ifBlank { "POCO X7" }
        val badge = config.deviceBadge.ifBlank { "Pro" }

        // Optional accessories on right in FULL mode: Map & QR
        var rightEdgeX = w - padX
        if (isFull) {
            val accessoryH = barH * 0.72f
            if (config.has(StampField.MAP) && data.mapBitmap != null) {
                val mapW = accessoryH * 1.15f
                val mapRect = RectF(rightEdgeX - mapW, centerY - accessoryH * 0.5f, rightEdgeX, centerY + accessoryH * 0.5f)
                drawMiniMap(canvas, mapRect, data.mapBitmap)
                rightEdgeX -= mapW + padX * 0.4f
            }
            if (config.has(StampField.QR_CODE)) {
                val qrW = accessoryH * 0.95f
                val qrRect = RectF(rightEdgeX - qrW, centerY - accessoryH * 0.5f, rightEdgeX, centerY + accessoryH * 0.5f)
                drawQrCode(canvas, qrRect, data, time, config)
                rightEdgeX -= qrW + padX * 0.4f
            }
        }

        var startX = padX

        if (hasYellowBlock) {
            val blockW = (w * 0.22f).coerceAtLeast(200f)
            val blockPaint = Paint().apply { color = ACCENT_YELLOW; style = Paint.Style.FILL }
            canvas.drawRect(0f, photo.height.toFloat(), blockW, outH.toFloat(), blockPaint)
            drawBrandWithBadge(canvas, blockW * 0.15f, centerY, brand, badge, Color.BLACK, Color.BLACK, barH * (if (isFull) 0.19f else 0.26f))
            startX = blockW + padX * 0.8f
        } else {
            if (hasAvatar || config.showAvatarOnFrame) {
                val avatarRadius = barH * (if (isFull) 0.22f else 0.28f)
                val avatarRect = RectF(startX, centerY - avatarRadius, startX + avatarRadius * 2f, centerY + avatarRadius)
                drawAvatar(canvas, avatarRect, config.logo)
                startX += avatarRadius * 2f + padX * 0.4f
            }
        }

        if (isFull) {
            // Three-row Full Information Layout
            val lineSpacing = barH * 0.28f
            val r1Y = centerY - lineSpacing * 0.85f
            val r2Y = centerY
            val r3Y = centerY + lineSpacing * 0.85f

            // Row 1 Left: Brand & Badge + Project Name
            val workLine = buildWorkAndProjectLine(config, cameraLabel)
            if (!hasYellowBlock) {
                drawBrandWithBadge(canvas, startX, r1Y, brand, badge, textColor, textColor, barH * 0.17f)
                if (workLine.isNotBlank()) {
                    val workPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = subColor
                        textSize = barH * 0.11f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    }
                    val brandW = estimateBrandBadgeWidth(brand, badge, barH * 0.17f)
                    canvas.drawText("  ·  $workLine", startX + brandW, r1Y, workPaint)
                }
            } else if (workLine.isNotBlank()) {
                val workPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = textColor
                    textSize = barH * 0.12f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                canvas.drawText(workLine, startX, r1Y, workPaint)
            }

            // Row 1 Right: EXIF specs
            if (config.showExifParams) {
                val exifPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = textColor
                    textSize = barH * 0.14f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.RIGHT
                }
                canvas.drawText(shot.formatExifSummary("  "), rightEdgeX, r1Y, exifPaint)
            }

            // Row 2 Left: Address & Locality
            val addrStr = buildAddressLine(data, config)
            val addrPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = subColor
                textSize = barH * 0.115f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            canvas.drawText(addrStr.ifBlank { "Location —" }, startX, r2Y, addrPaint)

            // Row 2 Right: Date & Time + GPS
            val dtStr = formatDateTime(time, config)
            val gpsStr = buildGpsAndLocationLine(data, config)
            val r2RightStr = if (gpsStr.isNotBlank() && config.showGpsOnFrame) "$dtStr  ·  $gpsStr" else dtStr
            val r2RightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = subColor
                textSize = barH * 0.115f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText(r2RightStr, rightEdgeX, r2Y, r2RightPaint)

            // Row 3 Left: Notes / Custom Motto
            val motto = config.note.ifBlank { config.customMotto }
            val mottoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isDark) ACCENT_YELLOW else 0xFFB26A00.toInt()
                textSize = barH * 0.105f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            canvas.drawText(motto.take(50), startX, r3Y, mottoPaint)

            // Row 3 Right: Weather & Sensors
            val weatherStr = buildWeatherAndSensorsLine(data, config)
            if (weatherStr.isNotBlank()) {
                val weatherPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = textColor
                    textSize = barH * 0.11f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    textAlign = Paint.Align.RIGHT
                }
                canvas.drawText(weatherStr, rightEdgeX, r3Y, weatherPaint)
            }

        } else {
            // Minimal Two-Row Layout
            if (!hasYellowBlock) {
                val titleY = centerY - barH * 0.08f
                drawBrandWithBadge(canvas, startX, titleY, brand, badge, textColor, textColor, barH * 0.25f)
                val dtStr = formatDateTime(time, config)
                val locStr = buildLocationString(data, config)
                val subLine = if (locStr.isNotBlank() && config.showGpsOnFrame) "$dtStr  ·  $locStr" else dtStr
                val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = subColor
                    textSize = barH * 0.16f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                }
                canvas.drawText(subLine, startX, centerY + barH * 0.26f, subPaint)
            }

            val rightX = w - padX
            val exifSummary = shot.formatExifSummary("  ")
            val exifPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor
                textSize = barH * 0.22f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            val subRightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = subColor
                textSize = barH * 0.16f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textAlign = Paint.Align.RIGHT
            }

            if (config.showExifParams) {
                canvas.drawText(exifSummary, rightX, centerY - barH * 0.08f, exifPaint)
                val rightDateStr = formatDateTimeShort(time)
                val gpsStr = buildGpsCoordinatesString(data, config)
                val line2 = if (gpsStr.isNotBlank() && config.showGpsOnFrame) "$rightDateStr  ·  $gpsStr" else rightDateStr
                canvas.drawText(line2, rightX, centerY + barH * 0.26f, subRightPaint)
            } else {
                val rightDateStr = formatDateTime(time, config)
                canvas.drawText(rightDateStr, rightX, centerY, exifPaint)
            }
        }

        return out
    }

    // --------------------------------------------------------------------------------------
    // 2. Full 4-Sided Exhibition White Mat Frame
    // --------------------------------------------------------------------------------------

    private fun renderFullMatFrame(
        photo: Bitmap,
        config: StampConfig,
        data: StampData,
        time: ZonedDateTime,
        shot: ShotParams,
        cameraLabel: String?,
        bgColor: Int,
        textColor: Int,
        hasAvatar: Boolean,
    ): Bitmap {
        val w = photo.width
        val isFull = config.frameDataDensity == FrameDataDensity.FULL
        val marginSide = (w * 0.038f).roundToInt()
        val marginTop = (w * 0.038f).roundToInt()
        val marginBottom = (w * if (isFull) 0.20f else 0.125f).roundToInt()

        val outW = w + marginSide * 2
        val outH = photo.height + marginTop + marginBottom
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        canvas.drawColor(bgColor)
        canvas.drawBitmap(photo, marginSide.toFloat(), marginTop.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))

        val bottomAreaY = marginTop + photo.height
        val centerY = bottomAreaY + marginBottom * 0.48f
        val padX = marginSide.toFloat() * 1.2f
        val subColor = if (textColor == Color.WHITE) 0xFFB0B0B0.toInt() else 0xFF666666.toInt()

        var rightEdgeX = outW - padX
        if (isFull) {
            val accessoryH = marginBottom * 0.65f
            if (config.has(StampField.MAP) && data.mapBitmap != null) {
                val mapW = accessoryH * 1.15f
                val mapRect = RectF(rightEdgeX - mapW, centerY - accessoryH * 0.5f, rightEdgeX, centerY + accessoryH * 0.5f)
                drawMiniMap(canvas, mapRect, data.mapBitmap)
                rightEdgeX -= mapW + padX * 0.35f
            }
            if (config.has(StampField.QR_CODE)) {
                val qrW = accessoryH * 0.95f
                val qrRect = RectF(rightEdgeX - qrW, centerY - accessoryH * 0.5f, rightEdgeX, centerY + accessoryH * 0.5f)
                drawQrCode(canvas, qrRect, data, time, config)
                rightEdgeX -= qrW + padX * 0.35f
            }
        }

        var startX = padX
        if (hasAvatar) {
            val avatarR = marginBottom * (if (isFull) 0.18f else 0.24f)
            val avatarRect = RectF(startX, centerY - avatarR, startX + avatarR * 2f, centerY + avatarR)
            drawAvatar(canvas, avatarRect, config.logo)
            startX += avatarR * 2f + padX * 0.4f
        }

        val brand = config.deviceBrand.ifBlank { "POCO X7" }
        val badge = config.deviceBadge.ifBlank { "Pro" }

        if (isFull) {
            val lineSpacing = marginBottom * 0.28f
            val r1Y = centerY - lineSpacing * 0.85f
            val r2Y = centerY
            val r3Y = centerY + lineSpacing * 0.85f

            drawBrandWithBadge(canvas, startX, r1Y, brand, badge, textColor, textColor, marginBottom * 0.16f)

            if (config.showExifParams) {
                val exifPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = textColor
                    textSize = marginBottom * 0.13f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.RIGHT
                }
                canvas.drawText(shot.formatExifSummary("  "), rightEdgeX, r1Y, exifPaint)
            }

            val addrStr = buildAddressLine(data, config)
            val addrPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = subColor
                textSize = marginBottom * 0.11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            canvas.drawText(addrStr.ifBlank { "Location —" }, startX, r2Y, addrPaint)

            val dtStr = formatDateTime(time, config)
            val gpsStr = buildGpsAndLocationLine(data, config)
            val r2RightStr = if (gpsStr.isNotBlank() && config.showGpsOnFrame) "$dtStr  ·  $gpsStr" else dtStr
            val r2RightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = subColor
                textSize = marginBottom * 0.11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText(r2RightStr, rightEdgeX, r2Y, r2RightPaint)

            val workLine = buildWorkAndProjectLine(config, cameraLabel)
            val workPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (textColor == Color.WHITE) ACCENT_YELLOW else 0xFFB26A00.toInt()
                textSize = marginBottom * 0.10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            canvas.drawText(workLine.take(50), startX, r3Y, workPaint)

            val weatherStr = buildWeatherAndSensorsLine(data, config)
            if (weatherStr.isNotBlank()) {
                val weatherPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = textColor
                    textSize = marginBottom * 0.105f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    textAlign = Paint.Align.RIGHT
                }
                canvas.drawText(weatherStr, rightEdgeX, r3Y, weatherPaint)
            }

        } else {
            drawBrandWithBadge(canvas, startX, centerY - marginBottom * 0.08f, brand, badge, textColor, textColor, marginBottom * 0.24f)
            val dtStr = formatDateTime(time, config)
            val locStr = buildLocationString(data, config)
            val leftSub = if (locStr.isNotBlank() && config.showGpsOnFrame) "$dtStr  ·  $locStr" else dtStr
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = subColor
                textSize = marginBottom * 0.15f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            canvas.drawText(leftSub, startX, centerY + marginBottom * 0.24f, subPaint)

            val rightX = outW - padX
            val exifSummary = shot.formatExifSummary("  ")
            val exifPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor
                textSize = marginBottom * 0.20f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            val subRightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = subColor
                textSize = marginBottom * 0.15f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textAlign = Paint.Align.RIGHT
            }

            if (config.showExifParams) {
                canvas.drawText(exifSummary, rightX, centerY - marginBottom * 0.08f, exifPaint)
                val gpsStr = buildGpsCoordinatesString(data, config)
                canvas.drawText(if (gpsStr.isNotBlank() && config.showGpsOnFrame) gpsStr else formatDateTimeShort(time), rightX, centerY + marginBottom * 0.24f, subRightPaint)
            } else {
                canvas.drawText(dtStr, rightX, centerY, exifPaint)
            }
        }

        return out
    }

    // --------------------------------------------------------------------------------------
    // 3. 35mm Negative Film Strip Frame
    // --------------------------------------------------------------------------------------

    private fun render35mmFilmStrip(
        photo: Bitmap,
        config: StampConfig,
        data: StampData,
        time: ZonedDateTime,
        shot: ShotParams,
        cameraLabel: String?,
    ): Bitmap {
        val w = photo.width
        val isFull = config.frameDataDensity == FrameDataDensity.FULL
        val marginSide = (w * 0.022f).roundToInt()
        val marginTop = (w * (if (isFull) 0.11f else 0.095f)).roundToInt()
        val marginBottom = (w * (if (isFull) 0.19f else 0.145f)).roundToInt()

        val outW = w + marginSide * 2
        val outH = photo.height + marginTop + marginBottom
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        canvas.drawColor(FILM_DARK)
        canvas.drawBitmap(photo, marginSide.toFloat(), marginTop.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))

        val sprocketW = outW * 0.028f
        val sprocketH = marginTop * 0.40f
        val sprocketYTop = marginTop * 0.25f
        drawSprocketsRow(canvas, outW.toFloat(), sprocketYTop, sprocketW, sprocketH)

        val sprocketYBottom = outH - marginBottom * 0.25f
        drawSprocketsRow(canvas, outW.toFloat(), sprocketYBottom, sprocketW, sprocketH)

        val amberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ACCENT_YELLOW
            textSize = marginBottom * (if (isFull) 0.115f else 0.18f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }

        val brand = config.deviceBrand.ifBlank { "POCO X7" }
        val badge = config.deviceBadge.ifBlank { "Pro" }

        if (isFull) {
            // Top Margin text between sprockets and photo
            val topTextY = marginTop * 0.85f
            val topPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFD54F.toInt()
                textSize = marginTop * 0.22f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            }
            val frameNo = "#%04d".format(config.number)
            val site = if (config.siteName.isNotBlank()) "  ·  SITE: ${config.siteName}" else ""
            val comp = data.azimuthDeg?.let { "  ·  🧭 %.0f° %s".format(Locale.US, it, WatermarkRenderer.direction(it)) } ?: ""
            canvas.drawText("$frameNo$site$comp", outW * 0.035f, topTextY, topPaint)

            val rightTopText = formatDateTime(time, config)
            topPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(rightTopText, outW * 0.965f, topTextY, topPaint)

            // Bottom Area text
            val textBaseY = marginTop + photo.height + marginBottom * 0.24f
            val lineSpacing = marginBottom * 0.22f

            amberPaint.textAlign = Paint.Align.LEFT
            drawBrandWithBadge(canvas, outW * 0.035f, textBaseY, brand, badge, ACCENT_YELLOW, ACCENT_YELLOW, marginBottom * 0.15f)

            amberPaint.textAlign = Paint.Align.RIGHT
            val exif = shot.formatExifSummary(" ")
            canvas.drawText(exif, outW * 0.965f, textBaseY, amberPaint)

            // Line 2: GPS + Address + Weather
            amberPaint.textAlign = Paint.Align.LEFT
            val gpsStr = buildGpsAndLocationLine(data, config)
            canvas.drawText(gpsStr, outW * 0.035f, textBaseY + lineSpacing, amberPaint)

            amberPaint.textAlign = Paint.Align.RIGHT
            val weatherStr = buildWeatherAndSensorsLine(data, config)
            canvas.drawText(weatherStr, outW * 0.965f, textBaseY + lineSpacing, amberPaint)

        } else {
            val textY = marginTop + photo.height + marginBottom * 0.32f
            val dtStr = formatDateTime(time, config)
            amberPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(dtStr, outW * 0.035f, textY, amberPaint)

            drawBrandWithBadge(canvas, outW * 0.5f, textY, brand, badge, ACCENT_YELLOW, ACCENT_YELLOW, marginBottom * 0.20f, isCenter = true)

            val rightX = outW * 0.965f
            amberPaint.textAlign = Paint.Align.RIGHT
            val exifSummary = shot.formatExifSummary(" ")
            val gpsStr = buildGpsCoordinatesString(data, config)
            val rightText = if (gpsStr.isNotBlank() && config.showGpsOnFrame) "$exifSummary  ·  $gpsStr" else exifSummary
            canvas.drawText(rightText, rightX, textY, amberPaint)
        }

        return out
    }

    private fun drawSprocketsRow(canvas: Canvas, totalWidth: Float, y: Float, w: Float, h: Float) {
        val fillPaint = Paint().apply { color = SPROCKET_COLOR; style = Paint.Style.FILL }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = SPROCKET_BORDER
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val count = 18
        val step = totalWidth / count
        val corner = w * 0.3f
        for (i in 0 until count) {
            val cx = i * step + step * 0.5f
            val rect = RectF(cx - w * 0.5f, y - h * 0.5f, cx + w * 0.5f, y + h * 0.5f)
            canvas.drawRoundRect(rect, corner, corner, fillPaint)
            canvas.drawRoundRect(rect, corner, corner, strokePaint)
        }
    }

    // --------------------------------------------------------------------------------------
    // 4. Signature POCO / Kodak Yellow Mat Frame
    // --------------------------------------------------------------------------------------

    private fun renderSignatureYellowMat(
        photo: Bitmap,
        config: StampConfig,
        data: StampData,
        time: ZonedDateTime,
        shot: ShotParams,
        cameraLabel: String?,
    ): Bitmap {
        return renderFullMatFrame(
            photo = photo,
            config = config,
            data = data,
            time = time,
            shot = shot,
            cameraLabel = cameraLabel,
            bgColor = ACCENT_YELLOW,
            textColor = Color.BLACK,
            hasAvatar = config.showAvatarOnFrame,
        )
    }

    // --------------------------------------------------------------------------------------
    // 5. Luxury Black Leatherette / Matte Frame
    // --------------------------------------------------------------------------------------

    private fun renderLuxuryBlackMat(
        photo: Bitmap,
        config: StampConfig,
        data: StampData,
        time: ZonedDateTime,
        shot: ShotParams,
        cameraLabel: String?,
    ): Bitmap {
        return renderFullMatFrame(
            photo = photo,
            config = config,
            data = data,
            time = time,
            shot = shot,
            cameraLabel = cameraLabel,
            bgColor = 0xFF0D0D0F.toInt(),
            textColor = Color.WHITE,
            hasAvatar = config.showAvatarOnFrame,
        )
    }

    // --------------------------------------------------------------------------------------
    // 6. Retro Vintage Film Frame (with Advance Chevrons)
    // --------------------------------------------------------------------------------------

    private fun renderRetroVintageFilm(
        photo: Bitmap,
        config: StampConfig,
        data: StampData,
        time: ZonedDateTime,
        shot: ShotParams,
        cameraLabel: String?,
    ): Bitmap {
        val w = photo.width
        val isFull = config.frameDataDensity == FrameDataDensity.FULL
        val marginSide = (w * 0.04f).roundToInt()
        val marginTop = (w * (if (isFull) 0.10f else 0.07f)).roundToInt()
        val marginBottom = (w * (if (isFull) 0.18f else 0.13f)).roundToInt()

        val outW = w + marginSide * 2
        val outH = photo.height + marginTop + marginBottom
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        canvas.drawColor(0xFF141311.toInt())
        canvas.drawBitmap(photo, marginSide.toFloat(), marginTop.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))

        val amberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFB300.toInt()
            textSize = marginTop * 0.38f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }

        // Top edge: ►► Chevron code & Site/Weather
        val topY = marginTop * 0.65f
        val siteStr = if (config.siteName.isNotBlank()) "  SITE: ${config.siteName}" else ""
        canvas.drawText("►► 36EXP$siteStr", marginSide.toFloat() * 1.2f, topY, amberPaint)

        amberPaint.textAlign = Paint.Align.RIGHT
        val topWeather = if (isFull) buildWeatherAndSensorsLine(data, config) else "ISO ${shot.iso ?: 400}"
        canvas.drawText(topWeather, outW - marginSide.toFloat() * 1.2f, topY, amberPaint)

        val bottomY = marginTop + photo.height + marginBottom * 0.45f
        amberPaint.textAlign = Paint.Align.LEFT
        val brand = config.deviceBrand.ifBlank { "POCO X7" }
        val badge = config.deviceBadge.ifBlank { "Pro" }

        if (isFull) {
            val lineSpacing = marginBottom * 0.26f
            drawBrandWithBadge(canvas, marginSide.toFloat() * 1.2f, bottomY - lineSpacing * 0.5f, brand, badge, 0xFFFFB300.toInt(), 0xFFFFB300.toInt(), marginBottom * 0.17f)

            amberPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(shot.formatExifSummary(" "), outW - marginSide.toFloat() * 1.2f, bottomY - lineSpacing * 0.5f, amberPaint)

            amberPaint.textAlign = Paint.Align.LEFT
            val gpsStr = buildGpsAndLocationLine(data, config)
            val addr = buildAddressLine(data, config)
            val subLine = listOf(gpsStr, addr).filter { it.isNotBlank() }.joinToString("  ·  ")
            canvas.drawText(subLine, marginSide.toFloat() * 1.2f, bottomY + lineSpacing * 0.5f, amberPaint)
        } else {
            canvas.drawText(formatDateTime(time, config), marginSide.toFloat() * 1.2f, bottomY, amberPaint)
            drawBrandWithBadge(canvas, outW * 0.5f, bottomY, brand, badge, 0xFFFFB300.toInt(), 0xFFFFB300.toInt(), marginBottom * 0.22f, isCenter = true)
            val rightX = outW - marginSide.toFloat() * 1.2f
            amberPaint.textAlign = Paint.Align.RIGHT
            val exifSummary = shot.formatExifSummary(" ")
            val gpsStr = buildGpsCoordinatesString(data, config)
            val rightText = if (gpsStr.isNotBlank() && config.showGpsOnFrame) "$exifSummary  $gpsStr" else exifSummary
            canvas.drawText(rightText, rightX, bottomY, amberPaint)
        }

        return out
    }

    // --------------------------------------------------------------------------------------
    // 7. Editorial Multi-Line Typography Frame
    // --------------------------------------------------------------------------------------

    private fun renderEditorialTypography(
        photo: Bitmap,
        config: StampConfig,
        data: StampData,
        time: ZonedDateTime,
        shot: ShotParams,
        cameraLabel: String?,
    ): Bitmap {
        val w = photo.width
        val isFull = config.frameDataDensity == FrameDataDensity.FULL
        val marginSide = (w * 0.035f).roundToInt()
        val marginTop = (w * 0.035f).roundToInt()
        val marginBottom = (w * (if (isFull) 0.22f else 0.175f)).roundToInt()

        val outW = w + marginSide * 2
        val outH = photo.height + marginTop + marginBottom
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        canvas.drawColor(0xFF101012.toInt())
        canvas.drawBitmap(photo, marginSide.toFloat(), marginTop.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))

        val startX = marginSide.toFloat() * 1.3f
        val baseTop = marginTop + photo.height + marginBottom * 0.20f
        val lineSpacing = marginBottom * 0.20f

        val brand = config.deviceBrand.ifBlank { "POCO X7" }
        val badge = config.deviceBadge.ifBlank { "Pro" }

        // Line 1: Brand [Badge] (White bold) + Project / Photo #
        drawBrandWithBadge(canvas, startX, baseTop, brand, badge, Color.WHITE, Color.WHITE, marginBottom * 0.17f)
        if (isFull) {
            val work = buildWorkAndProjectLine(config, cameraLabel)
            if (work.isNotBlank()) {
                val workPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFCCCCCC.toInt()
                    textSize = marginBottom * 0.12f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                }
                val brandW = estimateBrandBadgeWidth(brand, badge, marginBottom * 0.17f)
                canvas.drawText("  ·  $work", startX + brandW, baseTop, workPaint)
            }
        }

        // Line 2: EXIF specs (Coral / warm reddish) + Weather in FULL mode
        val coralPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE57373.toInt()
            textSize = marginBottom * 0.13f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
        val exifSummary = shot.formatExifSummary("  ")
        val line2Text = if (isFull) {
            val weather = buildWeatherAndSensorsLine(data, config)
            if (weather.isNotBlank()) "$exifSummary  ·  $weather" else exifSummary
        } else exifSummary
        canvas.drawText(line2Text, startX, baseTop + lineSpacing, coralPaint)

        // Line 3: Date, Time & Coordinates (Warm Amber)
        val amberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ACCENT_YELLOW
            textSize = marginBottom * 0.13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val dtStr = formatDateTime(time, config)
        val gpsStr = if (isFull) buildGpsAndLocationLine(data, config) else buildGpsCoordinatesString(data, config)
        val locStr = buildLocationString(data, config)
        val line3 = buildString {
            append(dtStr)
            if (gpsStr.isNotBlank() && config.showGpsOnFrame) append("  ·  $gpsStr")
            if (locStr.isNotBlank() && !isFull) append("  ·  $locStr")
        }
        canvas.drawText(line3, startX, baseTop + lineSpacing * 2f, amberPaint)

        // Line 4: Motto / Full Address (Warm Golden Italic)
        val line4Text = if (isFull) {
            val addr = buildAddressLine(data, config)
            val motto = config.note.ifBlank { config.customMotto }
            if (addr.isNotBlank()) "$addr  ·  $motto" else motto
        } else {
            config.note.ifBlank { config.customMotto.ifBlank { "Always believe that something wonderful is about to happen" } }
        }
        val mottoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFB74D.toInt()
            textSize = marginBottom * 0.12f
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        }
        canvas.drawText(line4Text.take(65), startX, baseTop + lineSpacing * 3f, mottoPaint)

        return out
    }

    // --------------------------------------------------------------------------------------
    // 8. Polaroid Right Sidebar Frame
    // --------------------------------------------------------------------------------------

    private fun renderPolaroidSidebar(
        photo: Bitmap,
        config: StampConfig,
        data: StampData,
        time: ZonedDateTime,
        shot: ShotParams,
        cameraLabel: String?,
    ): Bitmap {
        val h = photo.height
        val isFull = config.frameDataDensity == FrameDataDensity.FULL
        val sideW = (h * if (isFull) 0.28f else 0.22f).roundToInt().coerceIn(240, 850)
        val outW = photo.width + sideW
        val out = Bitmap.createBitmap(outW, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(photo, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))

        val barCenterX = photo.width + sideW * 0.5f
        val centerY = h * 0.5f

        val brand = config.deviceBrand.ifBlank { "POCO X7" }
        val badge = config.deviceBadge.ifBlank { "Pro" }

        val avatarR = sideW * 0.18f
        val avatarRect = RectF(barCenterX - avatarR, centerY - sideW * 0.65f, barCenterX + avatarR, centerY - sideW * 0.65f + avatarR * 2f)
        drawAvatar(canvas, avatarRect, config.logo)

        val brandY = centerY - sideW * 0.15f
        drawBrandWithBadge(canvas, barCenterX, brandY, brand, badge, Color.BLACK, Color.BLACK, sideW * 0.09f, isCenter = true)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF555555.toInt()
            textSize = sideW * 0.065f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }

        var curY = brandY + sideW * 0.13f
        val step = sideW * 0.11f

        canvas.drawText(shot.formatExifSummary(" "), barCenterX, curY, textPaint)
        curY += step

        canvas.drawText(formatDateTime(time, config), barCenterX, curY, textPaint)
        curY += step

        val gpsStr = buildGpsCoordinatesString(data, config)
        if (gpsStr.isNotBlank() && config.showGpsOnFrame) {
            canvas.drawText(gpsStr, barCenterX, curY, textPaint)
            curY += step
        }

        if (isFull) {
            val addr = buildAddressLine(data, config)
            if (addr.isNotBlank()) {
                canvas.drawText(addr.take(28), barCenterX, curY, textPaint)
                curY += step
            }
            val weather = buildWeatherAndSensorsLine(data, config)
            if (weather.isNotBlank()) {
                canvas.drawText(weather.take(28), barCenterX, curY, textPaint)
                curY += step
            }
            val work = buildWorkAndProjectLine(config, cameraLabel)
            if (work.isNotBlank()) {
                canvas.drawText(work.take(28), barCenterX, curY, textPaint)
                curY += step
            }
            if (config.has(StampField.MAP) && data.mapBitmap != null) {
                val mapSide = sideW * 0.55f
                val mapRect = RectF(barCenterX - mapSide * 0.5f, curY, barCenterX + mapSide * 0.5f, curY + mapSide)
                drawMiniMap(canvas, mapRect, data.mapBitmap)
            }
        }

        return out
    }

    // --------------------------------------------------------------------------------------
    // Visual Helpers & Data Builders
    // --------------------------------------------------------------------------------------

    private fun drawBrandWithBadge(
        canvas: Canvas,
        x: Float,
        y: Float,
        brand: String,
        badge: String,
        textColor: Int,
        badgeColor: Int,
        fontSize: Float,
        isCenter: Boolean = false,
    ) {
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = fontSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val brandW = brandPaint.measureText(brand)

        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = badgeColor
            textSize = fontSize * 0.65f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val badgeW = badgePaint.measureText(badge)
        val padH = fontSize * 0.35f
        val pillW = badgeW + padH * 2f
        val pillH = fontSize * 0.90f
        val spacing = fontSize * 0.35f

        val totalW = brandW + if (badge.isNotBlank()) spacing + pillW else 0f
        val startX = if (isCenter) x - totalW * 0.5f else x

        canvas.drawText(brand, startX, y, brandPaint)

        if (badge.isNotBlank()) {
            val pillX = startX + brandW + spacing
            val pillY = y - fontSize * 0.72f
            val pillRect = RectF(pillX, pillY, pillX + pillW, pillY + pillH)
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = badgeColor
                style = Paint.Style.STROKE
                strokeWidth = (fontSize * 0.08f).coerceAtLeast(2f)
            }
            canvas.drawRoundRect(pillRect, pillH * 0.40f, pillH * 0.40f, strokePaint)
            val textX = pillX + padH
            val textY = pillY + pillH * 0.72f
            canvas.drawText(badge, textX, textY, badgePaint)
        }
    }

    private fun estimateBrandBadgeWidth(brand: String, badge: String, fontSize: Float): Float {
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = fontSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val brandW = brandPaint.measureText(brand)
        if (badge.isBlank()) return brandW
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = fontSize * 0.65f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val badgeW = badgePaint.measureText(badge)
        val padH = fontSize * 0.35f
        val pillW = badgeW + padH * 2f
        val spacing = fontSize * 0.35f
        return brandW + spacing + pillW
    }

    private fun drawAvatar(canvas: Canvas, rect: RectF, customLogo: Bitmap?) {
        if (customLogo != null) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val path = Path().apply { addCircle(rect.centerX(), rect.centerY(), rect.width() * 0.5f, Path.Direction.CW) }
            canvas.save()
            canvas.clipPath(path)
            canvas.drawBitmap(customLogo, null, rect, p)
            canvas.restore()
        } else {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFCCCCCC.toInt() }
            canvas.drawCircle(rect.centerX(), rect.centerY(), rect.width() * 0.5f, bgPaint)
            val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            val cx = rect.centerX()
            val cy = rect.centerY()
            val r = rect.width() * 0.22f
            canvas.drawCircle(cx, cy - r * 0.5f, r, iconPaint)
            val bodyRect = RectF(cx - r * 1.5f, cy + r * 0.4f, cx + r * 1.5f, cy + r * 2.2f)
            canvas.drawArc(bodyRect, 180f, 180f, true, iconPaint)
        }
    }

    private fun drawMiniMap(canvas: Canvas, rect: RectF, mapBmp: Bitmap, cornerRadius: Float = 14f) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val clipPath = Path().apply { addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clipPath)
        val src = Rect(0, 0, mapBmp.width, mapBmp.height)
        canvas.drawBitmap(mapBmp, src, rect, paint)
        canvas.restore()

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            color = 0x66888888.toInt()
        }
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)
    }

    private fun drawQrCode(
        canvas: Canvas,
        rect: RectF,
        data: StampData,
        time: ZonedDateTime,
        config: StampConfig,
        cornerRadius: Float = 10f,
    ) {
        val payload = TamperProofSigner.buildPayload(
            data.latitude, data.longitude, data.altitude, time, data.plusCode, config.number,
        )
        val qrBmp = TamperProofSigner.generateQrBitmap(payload, rect.width().toInt().coerceAtLeast(64)) ?: return
        val bgPaint = Paint().apply { color = Color.WHITE }
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)
        val inner = RectF(rect.left + 4f, rect.top + 4f, rect.right - 4f, rect.bottom - 4f)
        val src = Rect(0, 0, qrBmp.width, qrBmp.height)
        canvas.drawBitmap(qrBmp, src, inner, Paint(Paint.FILTER_BITMAP_FLAG))
        qrBmp.recycle()
    }

    fun buildAddressLine(data: StampData, config: StampConfig): String {
        val parts = mutableListOf<String>()
        val flag = if (config.has(StampField.COUNTRY_FLAG)) WatermarkRenderer.flagEmoji(data.countryCode) else ""
        if (flag.isNotEmpty()) parts.add(flag)
        if (config.has(StampField.TITLE) && !data.title.isNullOrBlank()) {
            parts.add(data.title)
        }
        if (config.has(StampField.ADDRESS) && !data.address.isNullOrBlank()) {
            parts.add(data.address)
        } else if (config.has(StampField.SHORT_ADDRESS) && !data.shortAddress.isNullOrBlank()) {
            parts.add(data.shortAddress)
        }
        return parts.joinToString(" ").ifBlank { data.shortAddress ?: "" }
    }

    fun buildGpsAndLocationLine(data: StampData, config: StampConfig): String {
        val parts = mutableListOf<String>()
        val lat = data.latitude
        val lon = data.longitude
        if (config.has(StampField.LAT_LONG) && lat != null && lon != null) {
            parts.add(CoordinateConverter.format(lat, lon, config.coordinateFormat))
        }
        if (config.has(StampField.ALTITUDE) && data.altitude != null) {
            parts.add(String.format(Locale.US, "%.0fm", data.altitude))
        }
        if (config.has(StampField.ACCURACY) && data.accuracyM != null) {
            parts.add(String.format(Locale.US, "±%.0fm", data.accuracyM))
        }
        if (config.has(StampField.PLUS_CODE) && !data.plusCode.isNullOrBlank()) {
            parts.add(data.plusCode)
        }
        return parts.joinToString("  ·  ")
    }

    fun buildWeatherAndSensorsLine(data: StampData, config: StampConfig): String {
        val chips = mutableListOf<String>()
        if (config.has(StampField.TEMPERATURE) && data.temperatureC != null) {
            chips.add(String.format(Locale.US, "☀ %.1f°C", data.temperatureC))
        }
        if (config.has(StampField.HUMIDITY) && data.humidity != null) {
            chips.add("💧 ${data.humidity}%")
        }
        if (config.has(StampField.WIND) && data.windKmh != null) {
            chips.add(String.format(Locale.US, "💨 %.0fkm/h", data.windKmh))
        }
        if (config.has(StampField.PRESSURE) && data.pressureHpa != null) {
            chips.add(String.format(Locale.US, "⏲ %.0fhPa", data.pressureHpa))
        }
        if (config.has(StampField.AQI) && data.aqi != null) {
            chips.add("🍃 AQI ${data.aqi}")
        }
        if (config.has(StampField.SUN_TIMES) && data.sunrise != null && data.sunset != null) {
            chips.add("🌅 ${data.sunrise} 🌇 ${data.sunset}")
        }
        if (config.has(StampField.COMPASS) && data.azimuthDeg != null) {
            chips.add(String.format(Locale.US, "🧭 %.0f° %s", data.azimuthDeg, WatermarkRenderer.direction(data.azimuthDeg)))
        }
        if (config.has(StampField.MAGNETIC) && data.magneticUt != null) {
            chips.add(String.format(Locale.US, "🧲 %.1fµT", data.magneticUt))
        }
        if (config.has(StampField.SOUND_LEVEL) && data.soundDb != null) {
            chips.add(String.format(Locale.US, "🔊 %.0fdB", data.soundDb))
        }
        return chips.joinToString("   ")
    }

    fun buildWorkAndProjectLine(config: StampConfig, cameraLabel: String?): String {
        val parts = mutableListOf<String>()
        if (config.has(StampField.NUMBERING)) {
            parts.add("#%04d".format(config.number))
        }
        if (config.has(StampField.PROJECT) && config.siteName.isNotBlank()) {
            parts.add("Site: ${config.siteName}")
        }
        if (config.has(StampField.PERSON_NAME) && config.personName.isNotBlank()) {
            parts.add("By: ${config.personName}")
        }
        if (config.has(StampField.PHONE) && config.phone.isNotBlank()) {
            parts.add("☎ ${config.phone}")
        }
        if (config.has(StampField.CUSTOM_1) && config.custom1Label.isNotBlank()) {
            parts.add("${config.custom1Label}: ${config.custom1Value}")
        }
        if (config.has(StampField.CUSTOM_2) && config.custom2Label.isNotBlank()) {
            parts.add("${config.custom2Label}: ${config.custom2Value}")
        }
        if (config.has(StampField.NOTE) && config.note.isNotBlank()) {
            parts.add(config.note)
        }
        if (config.has(StampField.CAMERA_INFO) && !cameraLabel.isNullOrBlank()) {
            parts.add("Lens: $cameraLabel")
        }
        return parts.joinToString("  ·  ")
    }

    private fun formatDateTime(time: ZonedDateTime, config: StampConfig): String {
        return time.format(DateTimeFormatter.ofPattern(config.dateFormat.pattern, Locale.US))
    }

    private fun formatDateTimeShort(time: ZonedDateTime): String {
        return time.format(DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.US))
    }

    private fun buildLocationString(data: StampData, config: StampConfig): String {
        val parts = mutableListOf<String>()
        data.title?.let { parts.add(it) }
        data.shortAddress?.let { if (!parts.contains(it)) parts.add(it) }
        return parts.joinToString(", ")
    }

    private fun buildGpsCoordinatesString(data: StampData, config: StampConfig): String {
        val lat = data.latitude
        val lon = data.longitude
        if (lat == null || lon == null) return ""
        val coords = CoordinateConverter.format(lat, lon, config.coordinateFormat)
        val altStr = data.altitude?.let { " · ${it.roundToInt()}m" } ?: ""
        return "$coords$altStr"
    }
}
