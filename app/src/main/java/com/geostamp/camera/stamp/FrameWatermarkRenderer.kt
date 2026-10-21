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
        isPreview: Boolean = false,
    ): Bitmap {
        val shot = params ?: ShotParams()
        return when (config.watermarkStyle) {
            WatermarkStyle.STUDIO_WHITE_BAR -> renderBottomBar(photo, config, data, time, shot, cameraLabel, isDark = false, hasYellowBlock = false, hasStripe = false, hasAvatar = false, isPreview = isPreview)
            WatermarkStyle.ACCENT_BLOCK_BAR -> renderBottomBar(photo, config, data, time, shot, cameraLabel, isDark = true, hasYellowBlock = true, hasStripe = false, hasAvatar = false, isPreview = isPreview)
            WatermarkStyle.ACCENT_STRIPE_BAR -> renderBottomBar(photo, config, data, time, shot, cameraLabel, isDark = true, hasYellowBlock = false, hasStripe = true, hasAvatar = false, isPreview = isPreview)
            WatermarkStyle.AVATAR_WHITE_BAR -> renderBottomBar(photo, config, data, time, shot, cameraLabel, isDark = false, hasYellowBlock = false, hasStripe = false, hasAvatar = true, isPreview = isPreview)
            WatermarkStyle.EXHIBITION_WHITE_MAT -> renderFullMatFrame(photo, config, data, time, shot, cameraLabel, bgColor = Color.WHITE, textColor = Color.BLACK, hasAvatar = config.showAvatarOnFrame, isPreview = isPreview)
            WatermarkStyle.FILM_STRIP_35MM -> render35mmFilmStrip(photo, config, data, time, shot, cameraLabel, isPreview = isPreview)
            WatermarkStyle.SIGNATURE_YELLOW_MAT -> renderSignatureYellowMat(photo, config, data, time, shot, cameraLabel, isPreview = isPreview)
            WatermarkStyle.LUXURY_BLACK_MAT -> renderLuxuryBlackMat(photo, config, data, time, shot, cameraLabel, isPreview = isPreview)
            WatermarkStyle.RETRO_VINTAGE_FILM -> renderRetroVintageFilm(photo, config, data, time, shot, cameraLabel, isPreview = isPreview)
            WatermarkStyle.EDITORIAL_TYPOGRAPHY -> renderEditorialTypography(photo, config, data, time, shot, cameraLabel, isPreview = isPreview)
            WatermarkStyle.POLAROID_SIDEBAR -> renderPolaroidSidebar(photo, config, data, time, shot, cameraLabel, isPreview = isPreview)
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
        val framed = render(dummy, config, data, time, shot, cameraLabel, isPreview = true)
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
        isPreview: Boolean = false,
    ): Bitmap {
        val w = photo.width
        val isFull = config.frameDataDensity == FrameDataDensity.FULL
        val barH = (w * if (isFull) 0.185f else 0.115f).roundToInt().coerceIn(120, 750)
        val outH = if (isPreview) photo.height else photo.height + barH
        val out = Bitmap.createBitmap(w, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        if (!isPreview) {
            canvas.drawBitmap(photo, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        }

        val barTop = if (isPreview) (outH - barH).toFloat() else photo.height.toFloat()
        val barRect = RectF(0f, barTop, w.toFloat(), outH.toFloat())
        val bgPaint = Paint().apply {
            color = if (isDark) DARK_BG else Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawRect(barRect, bgPaint)

        if (hasStripe) {
            val stripePaint = Paint().apply { color = ACCENT_YELLOW; style = Paint.Style.FILL }
            val stripeH = (barH * 0.04f).coerceAtLeast(4f)
            canvas.drawRect(0f, barTop, w.toFloat(), barTop + stripeH, stripePaint)
        }

        val padX = w * 0.038f
        val centerY = barTop + barH * 0.5f
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

            // ---- Row 1: Brand & Badge + Project/Number (Left) | EXIF specs (Right) ----
            val exifStr = if (config.showExifParams) shot.formatExifSummary("  ") else ""
            val exifPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor
                textSize = barH * 0.13f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            val workLine = buildWorkAndProjectLine(config, cameraLabel)
            val workPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = subColor
                textSize = barH * 0.11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }

            if (!hasYellowBlock) {
                drawBrandWithBadge(canvas, startX, r1Y, brand, badge, textColor, textColor, barH * 0.16f)
                val brandW = estimateBrandBadgeWidth(brand, badge, barH * 0.16f)
                val row1LeftX = startX + brandW + padX * 0.3f
                drawLeftRightText(canvas, r1Y, row1LeftX, rightEdgeX, if (workLine.isNotBlank()) "·  $workLine" else "", workPaint, exifStr, exifPaint, padX * 0.4f)
            } else {
                drawLeftRightText(canvas, r1Y, startX, rightEdgeX, workLine, workPaint, exifStr, exifPaint, padX * 0.4f)
            }

            // ---- Row 2: Address / Locality (Left) | Date & Time (Right) ----
            val addrStr = buildAddressLine(data, config)
            val addrPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor
                textSize = barH * 0.115f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            val dtStr = formatDateTime(time, config)
            val dtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = subColor
                textSize = barH * 0.115f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textAlign = Paint.Align.RIGHT
            }
            drawLeftRightText(canvas, r2Y, startX, rightEdgeX, addrStr.ifBlank { "Location —" }, addrPaint, dtStr, dtPaint, padX * 0.4f)

            // ---- Row 3: GPS Coordinates & Elevation (Left) | Weather & Sensors (Right) ----
            val gpsCoords = buildGpsCoordinatesString(data, config)
            val customNote = config.note.ifBlank { config.customMotto }
            val row3Left = if (gpsCoords.isNotBlank() && config.showGpsOnFrame) {
                if (customNote.isNotBlank() && customNote != "Captured by GeoStamp Camera") "$gpsCoords  ·  $customNote" else gpsCoords
            } else {
                customNote
            }
            val row3LeftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isDark) ACCENT_YELLOW else 0xFFB26A00.toInt()
                textSize = barH * 0.105f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            val weatherStr = buildWeatherAndSensorsLine(data, config)
            val weatherPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = subColor
                textSize = barH * 0.105f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textAlign = Paint.Align.RIGHT
            }
            drawLeftRightText(canvas, r3Y, startX, rightEdgeX, row3Left, row3LeftPaint, weatherStr, weatherPaint, padX * 0.4f)

        } else {
            // Minimal Two-Row Layout
            val titleY = centerY - barH * 0.10f
            val subY = centerY + barH * 0.24f

            val exifSummary = if (config.showExifParams) shot.formatExifSummary("  ") else ""
            val exifPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor
                textSize = barH * 0.20f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }

            if (!hasYellowBlock) {
                drawBrandWithBadge(canvas, startX, titleY, brand, badge, textColor, textColor, barH * 0.22f)
                val brandW = estimateBrandBadgeWidth(brand, badge, barH * 0.22f)
                val row1LeftX = startX + brandW + padX * 0.3f
                drawLeftRightText(canvas, titleY, row1LeftX, rightEdgeX, "", exifPaint, exifSummary, exifPaint, padX * 0.4f)
            } else {
                drawLeftRightText(canvas, titleY, startX, rightEdgeX, "", exifPaint, exifSummary, exifPaint, padX * 0.4f)
            }

            // Row 2: Date & Time (Left) | Coordinates (Right)
            val dtStr = formatDateTime(time, config)
            val dtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = subColor
                textSize = barH * 0.15f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            val gpsStr = if (config.showGpsOnFrame) buildGpsCoordinatesString(data, config) else ""
            val gpsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = subColor
                textSize = barH * 0.15f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textAlign = Paint.Align.RIGHT
            }
            drawLeftRightText(canvas, subY, startX, rightEdgeX, dtStr, dtPaint, gpsStr, gpsPaint, padX * 0.4f)
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
        isPreview: Boolean = false,
    ): Bitmap {
        val w = photo.width
        val isFull = config.frameDataDensity == FrameDataDensity.FULL
        val marginSide = (w * 0.038f).roundToInt()
        val marginTop = (w * 0.038f).roundToInt()
        val marginBottom = (w * if (isFull) 0.20f else 0.125f).roundToInt()

        val outW = if (isPreview) w else w + marginSide * 2
        val outH = if (isPreview) photo.height else photo.height + marginTop + marginBottom
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        canvas.drawColor(bgColor)

        if (!isPreview) {
            canvas.drawBitmap(photo, marginSide.toFloat(), marginTop.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            val clearPaint = Paint().apply {
                xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
            }
            canvas.drawRect(
                RectF(
                    marginSide.toFloat(),
                    marginTop.toFloat(),
                    (outW - marginSide).toFloat(),
                    (outH - marginBottom).toFloat()
                ),
                clearPaint
            )
        }

        val bottomAreaY = if (isPreview) (outH - marginBottom).toFloat() else (marginTop + photo.height).toFloat()
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

            // ---- Row 1: Brand & Badge + Project/Number (Left) | EXIF specs (Right) ----
            val exifStr = if (config.showExifParams) shot.formatExifSummary("  ") else ""
            val exifPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor
                textSize = marginBottom * 0.13f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            val workLine = buildWorkAndProjectLine(config, cameraLabel)
            val workPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = subColor
                textSize = marginBottom * 0.11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }

            drawBrandWithBadge(canvas, startX, r1Y, brand, badge, textColor, textColor, marginBottom * 0.16f)
            val brandW = estimateBrandBadgeWidth(brand, badge, marginBottom * 0.16f)
            val row1LeftX = startX + brandW + padX * 0.3f
            drawLeftRightText(canvas, r1Y, row1LeftX, rightEdgeX, if (workLine.isNotBlank()) "·  $workLine" else "", workPaint, exifStr, exifPaint, padX * 0.4f)

            // ---- Row 2: Address / Locality (Left) | Date & Time (Right) ----
            val addrStr = buildAddressLine(data, config)
            val addrPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor
                textSize = marginBottom * 0.115f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            val dtStr = formatDateTime(time, config)
            val dtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = subColor
                textSize = marginBottom * 0.115f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textAlign = Paint.Align.RIGHT
            }
            drawLeftRightText(canvas, r2Y, startX, rightEdgeX, addrStr.ifBlank { "Location —" }, addrPaint, dtStr, dtPaint, padX * 0.4f)

            // ---- Row 3: GPS Coordinates & Elevation (Left) | Weather & Sensors (Right) ----
            val gpsCoords = buildGpsCoordinatesString(data, config)
            val customNote = config.note.ifBlank { config.customMotto }
            val row3Left = if (gpsCoords.isNotBlank() && config.showGpsOnFrame) {
                if (customNote.isNotBlank() && customNote != "Captured by GeoStamp Camera") "$gpsCoords  ·  $customNote" else gpsCoords
            } else {
                customNote
            }
            val row3LeftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (textColor == Color.WHITE) ACCENT_YELLOW else 0xFFB26A00.toInt()
                textSize = marginBottom * 0.105f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            val weatherStr = buildWeatherAndSensorsLine(data, config)
            val weatherPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = subColor
                textSize = marginBottom * 0.105f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textAlign = Paint.Align.RIGHT
            }
            drawLeftRightText(canvas, r3Y, startX, rightEdgeX, row3Left, row3LeftPaint, weatherStr, weatherPaint, padX * 0.4f)

        } else {
            // Minimal Two-Row Layout
            val titleY = centerY - marginBottom * 0.10f
            val subY = centerY + marginBottom * 0.24f

            val exifSummary = if (config.showExifParams) shot.formatExifSummary("  ") else ""
            val exifPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor
                textSize = marginBottom * 0.20f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }

            drawBrandWithBadge(canvas, startX, titleY, brand, badge, textColor, textColor, marginBottom * 0.22f)
            val brandW = estimateBrandBadgeWidth(brand, badge, marginBottom * 0.22f)
            val row1LeftX = startX + brandW + padX * 0.3f
            drawLeftRightText(canvas, titleY, row1LeftX, rightEdgeX, "", exifPaint, exifSummary, exifPaint, padX * 0.4f)

            // Row 2: Date & Time (Left) | Coordinates (Right)
            val dtStr = formatDateTime(time, config)
            val dtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = subColor
                textSize = marginBottom * 0.15f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            val gpsStr = if (config.showGpsOnFrame) buildGpsCoordinatesString(data, config) else ""
            val gpsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = subColor
                textSize = marginBottom * 0.15f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textAlign = Paint.Align.RIGHT
            }
            drawLeftRightText(canvas, subY, startX, rightEdgeX, dtStr, dtPaint, gpsStr, gpsPaint, padX * 0.4f)
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
        isPreview: Boolean = false,
    ): Bitmap {
        val w = photo.width
        val isFull = config.frameDataDensity == FrameDataDensity.FULL
        val marginSide = (w * 0.022f).roundToInt()
        val marginTop = (w * (if (isFull) 0.11f else 0.095f)).roundToInt()
        val marginBottom = (w * (if (isFull) 0.19f else 0.145f)).roundToInt()

        val outW = if (isPreview) w else w + marginSide * 2
        val outH = if (isPreview) photo.height else photo.height + marginTop + marginBottom
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        canvas.drawColor(FILM_DARK)

        if (!isPreview) {
            canvas.drawBitmap(photo, marginSide.toFloat(), marginTop.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            val clearPaint = Paint().apply {
                xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
            }
            canvas.drawRect(
                RectF(
                    marginSide.toFloat(),
                    marginTop.toFloat(),
                    (outW - marginSide).toFloat(),
                    (outH - marginBottom).toFloat()
                ),
                clearPaint
            )
        }

        val sprocketW = outW * 0.028f
        val sprocketH = (marginTop * 0.35f).coerceAtLeast(12f)
        val sprocketYTop = marginTop * 0.22f
        drawSprocketsRow(canvas, outW.toFloat(), sprocketYTop, sprocketW, sprocketH)

        val bottomSprocketH = (marginBottom * 0.16f).coerceAtLeast(12f)
        val sprocketYBottom = outH - (marginBottom * 0.12f)
        drawSprocketsRow(canvas, outW.toFloat(), sprocketYBottom, sprocketW, bottomSprocketH)

        val amberLeftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ACCENT_YELLOW
            textSize = marginBottom * (if (isFull) 0.115f else 0.18f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        val amberRightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ACCENT_YELLOW
            textSize = marginBottom * (if (isFull) 0.115f else 0.18f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }

        val brand = config.deviceBrand.ifBlank { "POCO X7" }
        val badge = config.deviceBadge.ifBlank { "Pro" }

        val leftX = outW * 0.035f
        val rightX = outW * 0.965f

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
            val leftTopText = "$frameNo$site$comp"
            val rightTopText = formatDateTime(time, config)
            val topPaintRight = Paint(topPaint).apply { textAlign = Paint.Align.RIGHT }
            drawLeftRightText(canvas, topTextY, leftX, rightX, leftTopText, topPaint, rightTopText, topPaintRight)

            // Bottom Area text
            val textBaseY = (if (isPreview) outH - marginBottom else marginTop + photo.height) + marginBottom * 0.24f
            val lineSpacing = marginBottom * 0.24f

            // Line 1: Brand & Badge (Left) vs EXIF (Right)
            drawBrandWithBadge(canvas, leftX, textBaseY, brand, badge, ACCENT_YELLOW, ACCENT_YELLOW, marginBottom * 0.15f)
            val brandBadgeW = estimateBrandBadgeWidth(brand, badge, marginBottom * 0.15f)
            val exif = if (config.showExifParams) shot.formatExifSummary(" ") else ""
            val safeExif = truncateText(exif, amberRightPaint, (rightX - (leftX + brandBadgeW + 20f)).coerceAtLeast(0f))
            canvas.drawText(safeExif, rightX, textBaseY, amberRightPaint)

            // Line 2: Address / GPS (Left) vs Weather & Sensors (Right)
            val gpsStr = buildGpsAndLocationLine(data, config)
            val addr = buildAddressLine(data, config)
            val leftLine2 = listOf(addr, gpsStr).filter { it.isNotBlank() }.joinToString("  ·  ")
            val weatherStr = buildWeatherAndSensorsLine(data, config)
            drawLeftRightText(canvas, textBaseY + lineSpacing, leftX, rightX, leftLine2, amberLeftPaint, weatherStr, amberRightPaint)

        } else {
            val textY = (if (isPreview) outH - marginBottom else marginTop + photo.height) + marginBottom * 0.32f
            val dtStr = formatDateTime(time, config)
            val exifSummary = if (config.showExifParams) shot.formatExifSummary(" ") else ""
            val gpsStr = buildGpsCoordinatesString(data, config)
            val rightText = if (gpsStr.isNotBlank() && config.showGpsOnFrame) "$exifSummary  ·  $gpsStr" else exifSummary

            val brandW = estimateBrandBadgeWidth(brand, badge, marginBottom * 0.20f)
            val centerLeftLimit = outW * 0.5f - brandW * 0.5f - 15f
            val centerRightLimit = outW * 0.5f + brandW * 0.5f + 15f

            val safeDt = truncateText(dtStr, amberLeftPaint, (centerLeftLimit - leftX).coerceAtLeast(0f))
            canvas.drawText(safeDt, leftX, textY, amberLeftPaint)

            drawBrandWithBadge(canvas, outW * 0.5f, textY, brand, badge, ACCENT_YELLOW, ACCENT_YELLOW, marginBottom * 0.20f, isCenter = true)

            val safeRight = truncateText(rightText, amberRightPaint, (rightX - centerRightLimit).coerceAtLeast(0f))
            canvas.drawText(safeRight, rightX, textY, amberRightPaint)
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
        isPreview: Boolean = false,
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
            isPreview = isPreview,
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
        isPreview: Boolean = false,
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
            isPreview = isPreview,
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
        isPreview: Boolean = false,
    ): Bitmap {
        val w = photo.width
        val isFull = config.frameDataDensity == FrameDataDensity.FULL
        val marginSide = (w * 0.04f).roundToInt()
        val marginTop = (w * (if (isFull) 0.10f else 0.07f)).roundToInt()
        val marginBottom = (w * (if (isFull) 0.18f else 0.13f)).roundToInt()

        val outW = if (isPreview) w else w + marginSide * 2
        val outH = if (isPreview) photo.height else photo.height + marginTop + marginBottom
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        canvas.drawColor(0xFF141311.toInt())

        if (!isPreview) {
            canvas.drawBitmap(photo, marginSide.toFloat(), marginTop.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            val clearPaint = Paint().apply {
                xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
            }
            canvas.drawRect(
                RectF(
                    marginSide.toFloat(),
                    marginTop.toFloat(),
                    (outW - marginSide).toFloat(),
                    (outH - marginBottom).toFloat()
                ),
                clearPaint
            )
        }

        val amberLeftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFB300.toInt()
            textSize = marginTop * 0.38f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        val amberRightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFB300.toInt()
            textSize = marginTop * 0.38f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }

        // Top edge: ►► Chevron code & Site/Weather
        val topY = marginTop * 0.65f
        val siteStr = if (config.siteName.isNotBlank()) "  SITE: ${config.siteName}" else ""
        val topCode = "►► 36EXP$siteStr"
        val topWeather = if (isFull) buildWeatherAndSensorsLine(data, config) else "ISO ${shot.iso ?: 400}"
        val leftX = marginSide.toFloat() * 1.2f
        val rightX = outW - marginSide.toFloat() * 1.2f
        drawLeftRightText(canvas, topY, leftX, rightX, topCode, amberLeftPaint, topWeather, amberRightPaint)

        val bottomY = (if (isPreview) outH - marginBottom else marginTop + photo.height) + marginBottom * 0.45f
        val brand = config.deviceBrand.ifBlank { "POCO X7" }
        val badge = config.deviceBadge.ifBlank { "Pro" }

        if (isFull) {
            val lineSpacing = marginBottom * 0.26f
            val brandBadgeW = estimateBrandBadgeWidth(brand, badge, marginBottom * 0.17f)
            drawBrandWithBadge(canvas, leftX, bottomY - lineSpacing * 0.5f, brand, badge, 0xFFFFB300.toInt(), 0xFFFFB300.toInt(), marginBottom * 0.17f)

            val exif = if (config.showExifParams) shot.formatExifSummary(" ") else ""
            val safeExif = truncateText(exif, amberRightPaint, (rightX - (leftX + brandBadgeW + 20f)).coerceAtLeast(0f))
            canvas.drawText(safeExif, rightX, bottomY - lineSpacing * 0.5f, amberRightPaint)

            val gpsStr = buildGpsAndLocationLine(data, config)
            val addr = buildAddressLine(data, config)
            val subLine = listOf(addr, gpsStr).filter { it.isNotBlank() }.joinToString("  ·  ")
            val dtStr = formatDateTime(time, config)
            drawLeftRightText(canvas, bottomY + lineSpacing * 0.5f, leftX, rightX, subLine, amberLeftPaint, dtStr, amberRightPaint)
        } else {
            val dtStr = formatDateTime(time, config)
            val exifSummary = if (config.showExifParams) shot.formatExifSummary(" ") else ""
            val gpsStr = buildGpsCoordinatesString(data, config)
            val rightText = if (gpsStr.isNotBlank() && config.showGpsOnFrame) "$exifSummary  $gpsStr" else exifSummary

            val brandW = estimateBrandBadgeWidth(brand, badge, marginBottom * 0.22f)
            val centerLeftLimit = outW * 0.5f - brandW * 0.5f - 15f
            val centerRightLimit = outW * 0.5f + brandW * 0.5f + 15f

            val safeDt = truncateText(dtStr, amberLeftPaint, (centerLeftLimit - leftX).coerceAtLeast(0f))
            canvas.drawText(safeDt, leftX, bottomY, amberLeftPaint)

            drawBrandWithBadge(canvas, outW * 0.5f, bottomY, brand, badge, 0xFFFFB300.toInt(), 0xFFFFB300.toInt(), marginBottom * 0.22f, isCenter = true)

            val safeRight = truncateText(rightText, amberRightPaint, (rightX - centerRightLimit).coerceAtLeast(0f))
            canvas.drawText(safeRight, rightX, bottomY, amberRightPaint)
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
        isPreview: Boolean = false,
    ): Bitmap {
        val w = photo.width
        val isFull = config.frameDataDensity == FrameDataDensity.FULL
        val marginSide = (w * 0.035f).roundToInt()
        val marginTop = (w * 0.035f).roundToInt()
        val marginBottom = (w * (if (isFull) 0.22f else 0.175f)).roundToInt()

        val outW = if (isPreview) w else w + marginSide * 2
        val outH = if (isPreview) photo.height else photo.height + marginTop + marginBottom
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        val bottomRect = RectF(0f, (outH - marginBottom).toFloat(), outW.toFloat(), outH.toFloat())
        val darkPaint = Paint().apply { color = 0xFF101012.toInt(); style = Paint.Style.FILL }
        canvas.drawRect(bottomRect, darkPaint)

        if (!isPreview) {
            canvas.drawColor(0xFF101012.toInt())
            canvas.drawBitmap(photo, marginSide.toFloat(), marginTop.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))
        }

        val startX = marginSide.toFloat() * 1.3f
        val maxRightX = outW - marginSide.toFloat() * 1.3f
        val maxLineWidth = (maxRightX - startX).coerceAtLeast(0f)
        val baseTop = (if (isPreview) outH - marginBottom else marginTop + photo.height) + marginBottom * 0.20f
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
                val remainingW = (maxRightX - (startX + brandW + 15f)).coerceAtLeast(0f)
                val safeWork = truncateText("  ·  $work", workPaint, remainingW)
                canvas.drawText(safeWork, startX + brandW, baseTop, workPaint)
            }
        }

        // Line 2: EXIF specs (Coral / warm reddish) + Weather in FULL mode
        val coralPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE57373.toInt()
            textSize = marginBottom * 0.13f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
        val exifSummary = if (config.showExifParams) shot.formatExifSummary("  ") else ""
        val line2Text = if (isFull) {
            val weather = buildWeatherAndSensorsLine(data, config)
            if (weather.isNotBlank()) "$exifSummary  ·  $weather" else exifSummary
        } else exifSummary
        val safeLine2 = truncateText(line2Text, coralPaint, maxLineWidth)
        canvas.drawText(safeLine2, startX, baseTop + lineSpacing, coralPaint)

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
        val safeLine3 = truncateText(line3, amberPaint, maxLineWidth)
        canvas.drawText(safeLine3, startX, baseTop + lineSpacing * 2f, amberPaint)

        // Line 4: Motto / Full Address (Warm Golden Italic)
        val line4Text = if (isFull) {
            val addr = buildAddressLine(data, config)
            val motto = config.note.ifBlank { config.customMotto }
            if (addr.isNotBlank() && motto.isNotBlank()) "$addr  ·  $motto" else addr.ifBlank { motto }
        } else {
            config.note.ifBlank { config.customMotto.ifBlank { "Always believe that something wonderful is about to happen" } }
        }
        val mottoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFB74D.toInt()
            textSize = marginBottom * 0.12f
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        }
        val safeLine4 = truncateText(line4Text, mottoPaint, maxLineWidth)
        canvas.drawText(safeLine4, startX, baseTop + lineSpacing * 3f, mottoPaint)

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
        isPreview: Boolean = false,
    ): Bitmap {
        val h = photo.height
        val isFull = config.frameDataDensity == FrameDataDensity.FULL
        val sideW = (h * if (isFull) 0.28f else 0.22f).roundToInt().coerceIn(240, 850)
        val outW = if (isPreview) photo.width else photo.width + sideW
        val out = Bitmap.createBitmap(outW, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        val sideLeft = (outW - sideW).toFloat()
        val sideRect = RectF(sideLeft, 0f, outW.toFloat(), h.toFloat())
        val sidePaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
        canvas.drawRect(sideRect, sidePaint)

        if (!isPreview) {
            canvas.drawBitmap(photo, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        }

        val barCenterX = sideLeft + sideW * 0.5f
        val centerY = h * 0.5f

        val brand = config.deviceBrand.ifBlank { "POCO X7" }
        val badge = config.deviceBadge.ifBlank { "Pro" }

        val avatarR = sideW * 0.18f
        val avatarRect = RectF(barCenterX - avatarR, centerY - sideW * 0.65f, barCenterX + avatarR, centerY - sideW * 0.65f + avatarR * 2f)
        drawAvatar(canvas, avatarRect, config.logo)

        val brandY = centerY - sideW * 0.15f
        drawBrandWithBadge(canvas, barCenterX, brandY, brand, badge, Color.BLACK, Color.BLACK, sideW * 0.09f, isCenter = true)

        val maxTextW = sideW * 0.86f

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF555555.toInt()
            textSize = sideW * 0.065f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }

        var curY = brandY + sideW * 0.13f
        val step = sideW * 0.11f

        val exif = if (config.showExifParams) shot.formatExifSummary(" ") else ""
        if (exif.isNotBlank()) {
            val safeExif = truncateText(exif, textPaint, maxTextW)
            canvas.drawText(safeExif, barCenterX, curY, textPaint)
            curY += step
        }

        val dt = truncateText(formatDateTime(time, config), textPaint, maxTextW)
        canvas.drawText(dt, barCenterX, curY, textPaint)
        curY += step

        val gpsStr = buildGpsCoordinatesString(data, config)
        if (gpsStr.isNotBlank() && config.showGpsOnFrame) {
            val safeGps = truncateText(gpsStr, textPaint, maxTextW)
            canvas.drawText(safeGps, barCenterX, curY, textPaint)
            curY += step
        }

        if (isFull) {
            val addr = buildAddressLine(data, config)
            if (addr.isNotBlank()) {
                val safeAddr = truncateText(addr, textPaint, maxTextW)
                canvas.drawText(safeAddr, barCenterX, curY, textPaint)
                curY += step
            }
            val weather = buildWeatherAndSensorsLine(data, config)
            if (weather.isNotBlank()) {
                val safeWeather = truncateText(weather, textPaint, maxTextW)
                canvas.drawText(safeWeather, barCenterX, curY, textPaint)
                curY += step
            }
            val work = buildWorkAndProjectLine(config, cameraLabel)
            if (work.isNotBlank()) {
                val safeWork = truncateText(work, textPaint, maxTextW)
                canvas.drawText(safeWork, barCenterX, curY, textPaint)
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

    /**
     * Efficiently truncates text to fit within [maxW] pixels using binary search.
     * Appends an ellipsis (…) if truncation is necessary.
     */
    fun truncateText(text: String, paint: Paint, maxW: Float): String {
        if (maxW <= 0f) return ""
        val trimmed = text.trim()
        if (trimmed.isEmpty() || paint.measureText(trimmed) <= maxW) return trimmed
        val ellipsis = "…"
        val ellW = paint.measureText(ellipsis)
        if (ellW >= maxW) return ""
        val targetW = maxW - ellW
        var low = 0
        var high = trimmed.length
        var best = 0
        while (low <= high) {
            val mid = (low + high) / 2
            val sub = trimmed.substring(0, mid)
            if (paint.measureText(sub) <= targetW) {
                best = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return trimmed.substring(0, best).trimEnd() + ellipsis
    }

    /**
     * Draws left-aligned and right-aligned text on the same baseline [y]
     * within the bounds [[leftX], [rightX]] with a guaranteed minimum gap [minGap].
     *
     * Overlapping is mathematically prevented: both sides are measured,
     * allocated disjoint bounding boxes, and cleanly truncated with (…) if needed.
     */
    fun drawLeftRightText(
        canvas: Canvas,
        y: Float,
        leftX: Float,
        rightX: Float,
        leftText: String,
        leftPaint: Paint,
        rightText: String,
        rightPaint: Paint,
        minGap: Float = 20f,
    ) {
        val availableW = rightX - leftX - minGap
        if (availableW <= 0f) return

        val cleanLeft = leftText.trim()
        val cleanRight = rightText.trim()

        if (cleanLeft.isEmpty() && cleanRight.isEmpty()) return

        if (cleanRight.isEmpty()) {
            val safeLeft = truncateText(cleanLeft, leftPaint, availableW + minGap)
            leftPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(safeLeft, leftX, y, leftPaint)
            return
        }

        if (cleanLeft.isEmpty()) {
            val safeRight = truncateText(cleanRight, rightPaint, availableW + minGap)
            rightPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(safeRight, rightX, y, rightPaint)
            return
        }

        val rawLeftW = leftPaint.measureText(cleanLeft)
        val rawRightW = rightPaint.measureText(cleanRight)

        if (rawLeftW + rawRightW <= availableW) {
            // Both fit comfortably with room to spare!
            leftPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(cleanLeft, leftX, y, leftPaint)
            rightPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(cleanRight, rightX, y, rightPaint)
            return
        }

        // Available space is smaller than combined text.
        // Allocate space proportionally, but guarantee each gets at least a sensible share.
        val maxRightW = if (rawRightW < availableW * 0.45f) {
            rawRightW
        } else {
            (availableW * 0.45f).coerceAtLeast(rawRightW.coerceAtMost(availableW * 0.55f))
        }
        val safeRight = truncateText(cleanRight, rightPaint, maxRightW)
        val actualRightW = rightPaint.measureText(safeRight)

        val maxLeftW = (availableW - actualRightW).coerceAtLeast(0f)
        val safeLeft = truncateText(cleanLeft, leftPaint, maxLeftW)

        leftPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(safeLeft, leftX, y, leftPaint)
        rightPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(safeRight, rightX, y, rightPaint)
    }

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
        if (config.has(StampField.NOTE) && config.note.isNotBlank() && config.note != "Captured by GeoStamp Camera") {
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
