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
import com.geostamp.camera.data.StampConfig
import com.geostamp.camera.data.StampData
import com.geostamp.camera.data.WatermarkStyle
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Renderer for studio, Leica, Xiaomi, POCO, and film frame watermark styles.
 * Draws high-resolution frames with EXIF shooting parameters, GPS coordinates,
 * elevation, timestamps, brand badges, and film sprockets.
 */
object FrameWatermarkRenderer {

    private const val ACCENT_YELLOW = 0xFFFFC107.toInt()
    private const val DARK_BG = 0xFF141416.toInt()
    private const val FILM_DARK = 0xFF18181A.toInt()
    private const val SPROCKET_COLOR = 0xFF0A0A0C.toInt()
    private const val SPROCKET_BORDER = 0xFF28282C.toInt()

    /**
     * Composes the photo into the selected watermark frame style.
     * Returns a new Bitmap containing the photo and frame.
     */
    fun render(
        photo: Bitmap,
        config: StampConfig,
        data: StampData,
        time: ZonedDateTime,
        params: ShotParams?,
    ): Bitmap {
        val shot = params ?: ShotParams()
        return when (config.watermarkStyle) {
            WatermarkStyle.STUDIO_WHITE_BAR -> renderBottomBar(photo, config, data, time, shot, isDark = false, hasYellowBlock = false, hasStripe = false, hasAvatar = false)
            WatermarkStyle.ACCENT_BLOCK_BAR -> renderBottomBar(photo, config, data, time, shot, isDark = true, hasYellowBlock = true, hasStripe = false, hasAvatar = false)
            WatermarkStyle.ACCENT_STRIPE_BAR -> renderBottomBar(photo, config, data, time, shot, isDark = true, hasYellowBlock = false, hasStripe = true, hasAvatar = false)
            WatermarkStyle.AVATAR_WHITE_BAR -> renderBottomBar(photo, config, data, time, shot, isDark = false, hasYellowBlock = false, hasStripe = false, hasAvatar = true)
            WatermarkStyle.EXHIBITION_WHITE_MAT -> renderFullMatFrame(photo, config, data, time, shot, bgColor = Color.WHITE, textColor = Color.BLACK, hasAvatar = config.showAvatarOnFrame)
            WatermarkStyle.FILM_STRIP_35MM -> render35mmFilmStrip(photo, config, data, time, shot)
            WatermarkStyle.SIGNATURE_YELLOW_MAT -> renderSignatureYellowMat(photo, config, data, time, shot)
            WatermarkStyle.LUXURY_BLACK_MAT -> renderLuxuryBlackMat(photo, config, data, time, shot)
            WatermarkStyle.RETRO_VINTAGE_FILM -> renderRetroVintageFilm(photo, config, data, time, shot)
            WatermarkStyle.EDITORIAL_TYPOGRAPHY -> renderEditorialTypography(photo, config, data, time, shot)
            WatermarkStyle.POLAROID_SIDEBAR -> renderPolaroidSidebar(photo, config, data, time, shot)
            WatermarkStyle.CLASSIC_GEO_BADGE -> photo // handled by WatermarkRenderer overlay
        }
    }

    /**
     * Renders a preview overlay of the watermark frame scaled for viewfinder display.
     */
    fun renderPreview(
        previewWidth: Int,
        frameHeight: Int,
        config: StampConfig,
        data: StampData,
        time: ZonedDateTime,
        params: ShotParams?,
    ): Bitmap? {
        val shot = params ?: ShotParams()
        val dummy = Bitmap.createBitmap(previewWidth, frameHeight, Bitmap.Config.ARGB_8888)
        val framed = render(dummy, config, data, time, shot)
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
        isDark: Boolean,
        hasYellowBlock: Boolean,
        hasStripe: Boolean,
        hasAvatar: Boolean,
    ): Bitmap {
        val w = photo.width
        val barH = (w * 0.115f).roundToInt().coerceIn(120, 520)
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

        // Yellow Stripe along the top edge of the bar
        if (hasStripe) {
            val stripePaint = Paint().apply {
                color = ACCENT_YELLOW
                style = Paint.Style.FILL
            }
            val stripeH = (barH * 0.05f).coerceAtLeast(4f)
            canvas.drawRect(0f, photo.height.toFloat(), w.toFloat(), photo.height + stripeH, stripePaint)
        }

        val padX = w * 0.04f
        val centerY = photo.height + barH * 0.5f
        val textColor = if (isDark) Color.WHITE else Color.BLACK
        val subColor = if (isDark) 0xFFB0B0B0.toInt() else 0xFF666666.toInt()

        val brand = config.deviceBrand.ifBlank { "POCO X7" }
        val badge = config.deviceBadge.ifBlank { "Pro" }

        var startX = padX

        // Accent Yellow Block on the left
        if (hasYellowBlock) {
            val blockW = (w * 0.22f).coerceAtLeast(200f)
            val blockPaint = Paint().apply { color = ACCENT_YELLOW; style = Paint.Style.FILL }
            canvas.drawRect(0f, photo.height.toFloat(), blockW, outH.toFloat(), blockPaint)
            drawBrandWithBadge(canvas, blockW * 0.15f, centerY, brand, badge, Color.BLACK, Color.BLACK, barH * 0.26f)
            startX = blockW + padX * 0.8f
        } else {
            // Optional Avatar circle on the left
            if (hasAvatar || config.showAvatarOnFrame) {
                val avatarRadius = barH * 0.28f
                val avatarRect = RectF(startX, centerY - avatarRadius, startX + avatarRadius * 2f, centerY + avatarRadius)
                drawAvatar(canvas, avatarRect, config.logo)
                startX += avatarRadius * 2f + padX * 0.4f
            }

            // Left side text: Brand + Badge, and below it Date/Location
            val titleY = centerY - barH * 0.08f
            drawBrandWithBadge(canvas, startX, titleY, brand, badge, textColor, textColor, barH * 0.25f)

            // Subtitle: Date & Time or Location
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

        // Right side: EXIF Shooting Parameters & Date/Coordinates
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
        bgColor: Int,
        textColor: Int,
        hasAvatar: Boolean,
    ): Bitmap {
        val w = photo.width
        val marginSide = (w * 0.038f).roundToInt()
        val marginTop = (w * 0.038f).roundToInt()
        val marginBottom = (w * 0.125f).roundToInt()

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

        var startX = padX
        if (hasAvatar) {
            val avatarR = marginBottom * 0.24f
            val avatarRect = RectF(startX, centerY - avatarR, startX + avatarR * 2f, centerY + avatarR)
            drawAvatar(canvas, avatarRect, config.logo)
            startX += avatarR * 2f + padX * 0.4f
        }

        val brand = config.deviceBrand.ifBlank { "POCO X7" }
        val badge = config.deviceBadge.ifBlank { "Pro" }
        drawBrandWithBadge(canvas, startX, centerY - marginBottom * 0.08f, brand, badge, textColor, textColor, marginBottom * 0.24f)

        // Subtitle line on left
        val dtStr = formatDateTime(time, config)
        val locStr = buildLocationString(data, config)
        val leftSub = if (locStr.isNotBlank() && config.showGpsOnFrame) "$dtStr  ·  $locStr" else dtStr
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = subColor
            textSize = marginBottom * 0.15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        canvas.drawText(leftSub, startX, centerY + marginBottom * 0.24f, subPaint)

        // Right side: EXIF + GPS
        val rightX = outW - padX
        val exifSummary = shot.formatExifSummary("  ")
        val exifPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = marginBottom * 0.22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val subRightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = subColor
            textSize = marginBottom * 0.15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
        }

        canvas.drawText(exifSummary, rightX, centerY - marginBottom * 0.08f, exifPaint)
        val rightDate = formatDateTimeShort(time)
        val gpsStr = buildGpsCoordinatesString(data, config)
        val rightSub = if (gpsStr.isNotBlank() && config.showGpsOnFrame) "$rightDate  ·  $gpsStr" else rightDate
        canvas.drawText(rightSub, rightX, centerY + marginBottom * 0.24f, subRightPaint)

        return out
    }

    // --------------------------------------------------------------------------------------
    // 3. 35mm Negative Film Strip Frame (with Sprockets)
    // --------------------------------------------------------------------------------------

    private fun render35mmFilmStrip(
        photo: Bitmap,
        config: StampConfig,
        data: StampData,
        time: ZonedDateTime,
        shot: ShotParams,
    ): Bitmap {
        val w = photo.width
        val marginSide = (w * 0.022f).roundToInt()
        val marginTop = (w * 0.095f).roundToInt()
        val marginBottom = (w * 0.145f).roundToInt()

        val outW = w + marginSide * 2
        val outH = photo.height + marginTop + marginBottom
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        canvas.drawColor(FILM_DARK)
        canvas.drawBitmap(photo, marginSide.toFloat(), marginTop.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))

        // Draw top sprocket perforations
        val sprocketW = outW * 0.028f
        val sprocketH = marginTop * 0.45f
        val sprocketYTop = marginTop * 0.28f
        drawSprocketsRow(canvas, outW.toFloat(), sprocketYTop, sprocketW, sprocketH)

        // Draw bottom sprocket perforations along the very bottom
        val sprocketYBottom = outH - marginBottom * 0.42f
        drawSprocketsRow(canvas, outW.toFloat(), sprocketYBottom, sprocketW, sprocketH)

        // In the amber film strip area between photo bottom and bottom sprockets:
        val textY = marginTop + photo.height + marginBottom * 0.32f
        val amberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ACCENT_YELLOW
            textSize = marginBottom * 0.18f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }

        // Left: Date & Time
        val dtStr = formatDateTime(time, config)
        amberPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(dtStr, outW * 0.035f, textY, amberPaint)

        // Center: Brand & Badge
        val brand = config.deviceBrand.ifBlank { "POCO X7" }
        val badge = config.deviceBadge.ifBlank { "Pro" }
        drawBrandWithBadge(canvas, outW * 0.5f, textY, brand, badge, ACCENT_YELLOW, ACCENT_YELLOW, marginBottom * 0.20f, isCenter = true)

        // Right: EXIF parameters + GPS coordinates
        val rightX = outW * 0.965f
        amberPaint.textAlign = Paint.Align.RIGHT
        val exifSummary = shot.formatExifSummary(" ")
        val gpsStr = buildGpsCoordinatesString(data, config)
        val rightText = if (gpsStr.isNotBlank() && config.showGpsOnFrame) "$exifSummary  ·  $gpsStr" else exifSummary
        canvas.drawText(rightText, rightX, textY, amberPaint)

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
    ): Bitmap {
        val w = photo.width
        val marginSide = (w * 0.038f).roundToInt()
        val marginTop = (w * 0.065f).roundToInt()
        val marginBottom = (w * 0.125f).roundToInt()

        val outW = w + marginSide * 2
        val outH = photo.height + marginTop + marginBottom
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        canvas.drawColor(ACCENT_YELLOW)
        canvas.drawBitmap(photo, marginSide.toFloat(), marginTop.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))

        // Top-left: Timestamp in black monospace font
        val dtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = marginTop * 0.36f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        canvas.drawText(formatDateTime(time, config), marginSide.toFloat() * 1.2f, marginTop * 0.65f, dtPaint)

        // Bottom area:
        val centerY = marginTop + photo.height + marginBottom * 0.5f
        val padX = marginSide.toFloat() * 1.2f
        val brand = config.deviceBrand.ifBlank { "POCO X7" }
        val badge = config.deviceBadge.ifBlank { "Pro" }

        drawBrandWithBadge(canvas, padX, centerY, brand, badge, Color.BLACK, Color.BLACK, marginBottom * 0.25f)

        val rightX = outW - padX
        val exifSummary = shot.formatExifSummary("  ")
        val gpsStr = buildGpsCoordinatesString(data, config)
        val rightLine = if (gpsStr.isNotBlank() && config.showGpsOnFrame) "$exifSummary   $gpsStr" else exifSummary

        val rightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = marginBottom * 0.22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(rightLine, rightX, centerY, rightPaint)

        return out
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
    ): Bitmap {
        val w = photo.width
        val marginSide = (w * 0.045f).roundToInt()
        val marginTop = (w * 0.045f).roundToInt()
        val marginBottom = (w * 0.135f).roundToInt()

        val outW = w + marginSide * 2
        val outH = photo.height + marginTop + marginBottom
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        canvas.drawColor(DARK_BG)
        canvas.drawBitmap(photo, marginSide.toFloat(), marginTop.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))

        val centerY = marginTop + photo.height + marginBottom * 0.42f
        val brand = config.deviceBrand.ifBlank { "POCO X7" }
        val badge = config.deviceBadge.ifBlank { "Pro" }

        // Centered Brand with badge
        drawBrandWithBadge(canvas, outW * 0.5f, centerY, brand, badge, Color.WHITE, Color.WHITE, marginBottom * 0.24f, isCenter = true)

        // Centered EXIF and Coordinates line below
        val exifSummary = shot.formatExifSummary(" · ")
        val gpsStr = buildGpsCoordinatesString(data, config)
        val dtStr = formatDateTime(time, config)
        val subLine = buildString {
            append(dtStr)
            if (config.showExifParams) append("  |  $exifSummary")
            if (gpsStr.isNotBlank() && config.showGpsOnFrame) append("  |  $gpsStr")
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF888888.toInt()
            textSize = marginBottom * 0.13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(subLine, outW * 0.5f, centerY + marginBottom * 0.28f, subPaint)

        return out
    }

    // --------------------------------------------------------------------------------------
    // 6. Retro Vintage Film Frame (with ►► advance marker)
    // --------------------------------------------------------------------------------------

    private fun renderRetroVintageFilm(
        photo: Bitmap,
        config: StampConfig,
        data: StampData,
        time: ZonedDateTime,
        shot: ShotParams,
    ): Bitmap {
        val w = photo.width
        val marginSide = (w * 0.035f).roundToInt()
        val marginTop = (w * 0.055f).roundToInt()
        val marginBottom = (w * 0.125f).roundToInt()

        val outW = w + marginSide * 2
        val outH = photo.height + marginTop + marginBottom
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        canvas.drawColor(0xFF111113.toInt())
        canvas.drawBitmap(photo, marginSide.toFloat(), marginTop.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))

        // Top-left: Film advance markers "►►" in amber
        val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFB300.toInt()
            textSize = marginTop * 0.40f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("►►", marginSide.toFloat() * 1.2f, marginTop * 0.70f, markerPaint)

        val centerY = marginTop + photo.height + marginBottom * 0.5f
        val amberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFB300.toInt()
            textSize = marginBottom * 0.20f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }

        // Left: Date & Time
        amberPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(formatDateTime(time, config), marginSide.toFloat() * 1.2f, centerY, amberPaint)

        // Center: Brand & Badge
        val brand = config.deviceBrand.ifBlank { "POCO X7" }
        val badge = config.deviceBadge.ifBlank { "Pro" }
        drawBrandWithBadge(canvas, outW * 0.5f, centerY, brand, badge, 0xFFFFB300.toInt(), 0xFFFFB300.toInt(), marginBottom * 0.22f, isCenter = true)

        // Right: EXIF & GPS
        val rightX = outW - marginSide.toFloat() * 1.2f
        amberPaint.textAlign = Paint.Align.RIGHT
        val exifSummary = shot.formatExifSummary(" ")
        val gpsStr = buildGpsCoordinatesString(data, config)
        val rightText = if (gpsStr.isNotBlank() && config.showGpsOnFrame) "$exifSummary  $gpsStr" else exifSummary
        canvas.drawText(rightText, rightX, centerY, amberPaint)

        return out
    }

    // --------------------------------------------------------------------------------------
    // 7. Editorial Multi-Line Typography Frame (with Motto / Project Note)
    // --------------------------------------------------------------------------------------

    private fun renderEditorialTypography(
        photo: Bitmap,
        config: StampConfig,
        data: StampData,
        time: ZonedDateTime,
        shot: ShotParams,
    ): Bitmap {
        val w = photo.width
        val marginSide = (w * 0.035f).roundToInt()
        val marginTop = (w * 0.035f).roundToInt()
        val marginBottom = (w * 0.175f).roundToInt()

        val outW = w + marginSide * 2
        val outH = photo.height + marginTop + marginBottom
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        canvas.drawColor(0xFF101012.toInt())
        canvas.drawBitmap(photo, marginSide.toFloat(), marginTop.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))

        val startX = marginSide.toFloat() * 1.3f
        val baseTop = marginTop + photo.height + marginBottom * 0.22f
        val lineSpacing = marginBottom * 0.21f

        val brand = config.deviceBrand.ifBlank { "POCO X7" }
        val badge = config.deviceBadge.ifBlank { "Pro" }

        // Line 1: Brand [Badge] (White bold)
        drawBrandWithBadge(canvas, startX, baseTop, brand, badge, Color.WHITE, Color.WHITE, marginBottom * 0.18f)

        // Line 2: EXIF specs (Coral / warm reddish)
        val coralPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE57373.toInt()
            textSize = marginBottom * 0.14f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
        val exifSummary = shot.formatExifSummary("  ")
        canvas.drawText(exifSummary, startX, baseTop + lineSpacing, coralPaint)

        // Line 3: Date, Time & Coordinates (Warm Amber)
        val amberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ACCENT_YELLOW
            textSize = marginBottom * 0.14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val dtStr = formatDateTime(time, config)
        val gpsStr = buildGpsCoordinatesString(data, config)
        val locStr = buildLocationString(data, config)
        val line3 = buildString {
            append(dtStr)
            if (gpsStr.isNotBlank() && config.showGpsOnFrame) append("  ·  $gpsStr")
            if (locStr.isNotBlank()) append("  ·  $locStr")
        }
        canvas.drawText(line3, startX, baseTop + lineSpacing * 2f, amberPaint)

        // Line 4: Motto / Inspection Note (Warm Golden Italic)
        val mottoText = config.note.ifBlank { config.customMotto.ifBlank { "Always believe that something wonderful is about to happen" } }
        val mottoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFB74D.toInt()
            textSize = marginBottom * 0.13f
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        }
        canvas.drawText(mottoText, startX, baseTop + lineSpacing * 3f, mottoPaint)

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
    ): Bitmap {
        val h = photo.height
        val sideW = (h * 0.26f).roundToInt().coerceIn(240, 800)
        val outW = photo.width + sideW
        val out = Bitmap.createBitmap(outW, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(photo, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))

        val barCenterX = photo.width + sideW * 0.5f
        val centerY = h * 0.5f

        val brand = config.deviceBrand.ifBlank { "POCO X7" }
        val badge = config.deviceBadge.ifBlank { "Pro" }

        // Avatar circle
        val avatarR = sideW * 0.22f
        val avatarRect = RectF(barCenterX - avatarR, centerY - sideW * 0.55f, barCenterX + avatarR, centerY - sideW * 0.55f + avatarR * 2f)
        drawAvatar(canvas, avatarRect, config.logo)

        // Brand [Badge] centered
        val brandY = centerY - sideW * 0.05f
        drawBrandWithBadge(canvas, barCenterX, brandY, brand, badge, Color.BLACK, Color.BLACK, sideW * 0.11f, isCenter = true)

        // EXIF specs centered
        val exifSummary = shot.formatExifSummary(" ")
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF555555.toInt()
            textSize = sideW * 0.075f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(exifSummary, barCenterX, brandY + sideW * 0.15f, textPaint)

        // Date and Time
        val dtStr = formatDateTime(time, config)
        canvas.drawText(dtStr, barCenterX, brandY + sideW * 0.28f, textPaint)

        // GPS & Altitude
        val gpsStr = buildGpsCoordinatesString(data, config)
        if (gpsStr.isNotBlank() && config.showGpsOnFrame) {
            canvas.drawText(gpsStr, barCenterX, brandY + sideW * 0.41f, textPaint)
        }

        return out
    }

    // --------------------------------------------------------------------------------------
    // Visual Helpers: Avatar, Brand Badge, Formatting
    // --------------------------------------------------------------------------------------

    private fun drawBrandWithBadge(
        canvas: Canvas,
        x: Float,
        y: Float,
        brand: String,
        badge: String,
        textColor: Int,
        badgeBorderColor: Int,
        fontSize: Float,
        isCenter: Boolean = false,
    ) {
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = fontSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = fontSize * 0.70f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = badgeBorderColor
            style = Paint.Style.STROKE
            strokeWidth = (fontSize * 0.08f).coerceAtLeast(1.5f)
        }

        val brandW = brandPaint.measureText(brand)
        val badgeW = badgePaint.measureText(badge)
        val padH = fontSize * 0.35f
        val pillW = badgeW + padH * 2f
        val spacing = fontSize * 0.25f

        val totalW = brandW + spacing + pillW
        val drawX = if (isCenter) x - totalW * 0.5f else x

        canvas.drawText(brand, drawX, y, brandPaint)

        val pillLeft = drawX + brandW + spacing
        val pillH = fontSize * 0.90f
        val pillTop = y - pillH * 0.85f
        val pillRect = RectF(pillLeft, pillTop, pillLeft + pillW, pillTop + pillH)
        val radius = pillH * 0.40f

        canvas.drawRoundRect(pillRect, radius, radius, borderPaint)
        val badgeTextX = pillLeft + padH
        val badgeTextY = pillTop + pillH * 0.72f
        canvas.drawText(badge, badgeTextX, badgeTextY, badgePaint)
    }

    private fun drawAvatar(canvas: Canvas, rect: RectF, customLogo: Bitmap?) {
        if (customLogo != null && !customLogo.isRecycled) {
            val clipPath = Path().apply { addOval(rect, Path.Direction.CW) }
            canvas.save()
            canvas.clipPath(clipPath)
            canvas.drawBitmap(customLogo, null, rect, Paint(Paint.FILTER_BITMAP_FLAG))
            canvas.restore()
            return
        }

        // Draw default neutral avatar (head silhouette + shoulders arc)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFD0D0D2.toInt()
            style = Paint.Style.FILL
        }
        canvas.drawOval(rect, bgPaint)

        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val headRadius = rect.width() * 0.24f
        val headCenterY = rect.centerY() - rect.height() * 0.12f
        canvas.drawCircle(rect.centerX(), headCenterY, headRadius, iconPaint)

        val shoulderRect = RectF(
            rect.centerX() - rect.width() * 0.38f,
            rect.centerY() + rect.height() * 0.06f,
            rect.centerX() + rect.width() * 0.38f,
            rect.bottom + rect.height() * 0.20f,
        )
        canvas.save()
        val circlePath = Path().apply { addOval(rect, Path.Direction.CW) }
        canvas.clipPath(circlePath)
        canvas.drawRoundRect(shoulderRect, rect.width() * 0.38f, rect.width() * 0.38f, iconPaint)
        canvas.restore()
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
