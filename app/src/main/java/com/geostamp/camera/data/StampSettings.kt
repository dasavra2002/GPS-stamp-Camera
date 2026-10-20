package com.geostamp.camera.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.geostamp.camera.R
import java.io.File

enum class StampGroup(val title: String) {
    LOCATION("Location"),
    TIME("Date, time & numbering"),
    BRANDING("Logo & custom text"),
    WEATHER("Weather"),
    SENSORS("Sensors & camera"),
}

/** Every item that can appear on the watermark. Each has its own on/off switch. */
enum class StampField(
    val key: String,
    val label: String,
    val group: StampGroup,
    val defaultOn: Boolean,
) {
    MAP("f_map", "Map thumbnail", StampGroup.LOCATION, true),
    TITLE("f_title", "Place title (City, State, Country)", StampGroup.LOCATION, true),
    COUNTRY_FLAG("f_flag", "Country flag", StampGroup.LOCATION, true),
    SHORT_ADDRESS("f_short_address", "Short address", StampGroup.LOCATION, false),
    ADDRESS("f_address", "Full address", StampGroup.LOCATION, true),
    LAT_LONG("f_latlong", "Lat/Long", StampGroup.LOCATION, true),
    PLUS_CODE("f_pluscode", "Plus Code", StampGroup.LOCATION, true),
    ALTITUDE("f_altitude", "Altitude", StampGroup.LOCATION, false),
    ACCURACY("f_accuracy", "GPS accuracy", StampGroup.LOCATION, false),

    DATE_TIME("f_datetime", "Date & time", StampGroup.TIME, true),
    TIME_ZONE("f_timezone", "Time zone (GMT +05:30)", StampGroup.TIME, true),
    NUMBERING("f_numbering", "Numbering (photo counter)", StampGroup.TIME, true),

    LOGO("f_logo", "Logo", StampGroup.BRANDING, true),
    QR_CODE("f_qr", "Tamper-proof QR code (Signature)", StampGroup.BRANDING, false),
    PROJECT("f_project", "Project / Site", StampGroup.BRANDING, true),
    NOTE("f_note", "Note / Hashtag", StampGroup.BRANDING, true),
    PERSON_NAME("f_person", "Person name", StampGroup.BRANDING, true),
    PHONE("f_phone", "Contact number", StampGroup.BRANDING, true),
    CUSTOM_1("f_custom1", "Custom Field 1", StampGroup.BRANDING, false),
    CUSTOM_2("f_custom2", "Custom Field 2", StampGroup.BRANDING, false),

    TEMPERATURE("f_temp", "Temperature", StampGroup.WEATHER, true),
    WIND("f_wind", "Wind", StampGroup.WEATHER, true),
    HUMIDITY("f_humidity", "Humidity", StampGroup.WEATHER, true),
    PRESSURE("f_pressure", "Pressure", StampGroup.WEATHER, true),
    AQI("f_aqi", "Air Quality (AQI)", StampGroup.WEATHER, false),
    SUN_TIMES("f_suntimes", "Sunrise & Sunset", StampGroup.WEATHER, false),

    COMPASS("f_compass", "Compass", StampGroup.SENSORS, true),
    MAGNETIC("f_magnetic", "Magnetic field", StampGroup.SENSORS, true),
    SOUND_LEVEL("f_sound", "Sound level (needs microphone)", StampGroup.SENSORS, false),
    CAMERA_INFO("f_camera", "Camera / lens used", StampGroup.SENSORS, false),
}

interface Labeled { val label: String }

enum class MapType(override val label: String) : Labeled {
    SATELLITE("Satellite"), STREET("Street"), TERRAIN("Terrain")
}

enum class FontStyle(override val label: String, val family: String, val bold: Boolean) : Labeled {
    SANS("Sans", "sans-serif", false),
    SANS_BOLD("Sans Bold", "sans-serif", true),
    CONDENSED("Condensed", "sans-serif-condensed", false),
    SERIF("Serif", "serif", false),
    MONO("Monospace", "monospace", false),
}

enum class StampColor(override val label: String, val text: Int, val multi: Boolean) : Labeled {
    MULTIPLE("Multiple", 0xFFFFFFFF.toInt(), true),
    WHITE("White", 0xFFFFFFFF.toInt(), false),
    YELLOW("Yellow", 0xFFFFD54F.toInt(), false),
    CYAN("Cyan", 0xFF4DD0E1.toInt(), false),
    GREEN("Green", 0xFF81C784.toInt(), false),
    BLACK("Black", 0xFF111111.toInt(), false),
}

enum class StampBackground(override val label: String, val color: Int) : Labeled {
    DARK("Dark", 0x99000000.toInt()),
    DARKER("Darker", 0xCC000000.toInt()),
    LIGHT("Light", 0xB3FFFFFF.toInt()),
    NONE("None", 0x00000000),
}

enum class StampSize(override val label: String, val scale: Float) : Labeled {
    EXTRA_SMALL("Extra Small", 0.7f),
    SMALL("Small", 0.85f),
    MEDIUM("Medium", 1.0f),
    LARGE("Large", 1.2f),
}

enum class StampPosition(override val label: String) : Labeled { BOTTOM("Bottom"), TOP("Top"), CENTER("Center") }

enum class MapPosition(override val label: String) : Labeled { LEFT("Left"), RIGHT("Right") }

enum class DateFormat(override val label: String, val pattern: String) : Labeled {
    LONG("Saturday, 17/10/2026 11:14 PM", "EEEE, dd/MM/yyyy hh:mm a"),
    DMY_24("17/10/2026 23:14", "dd/MM/yyyy HH:mm"),
    MDY("10/17/2026 11:14 PM", "MM/dd/yyyy hh:mm a"),
    MONTH_NAME("17 Oct 2026, 11:14:05 PM", "dd MMM yyyy, hh:mm:ss a"),
    ISO("2026-10-17 23:14:05", "yyyy-MM-dd HH:mm:ss"),
}

enum class StampLanguage(override val label: String, val code: String) : Labeled {
    ENGLISH("English", "en"),
    HINDI("Hindi (हिन्दी)", "hi"),
    BENGALI("Bengali (বাংলা)", "bn"),
    MARATHI("Marathi (मराठी)", "mr"),
    GUJARATI("Gujarati (ગુજરાતી)", "gu"),
    TAMIL("Tamil (தமிழ்)", "ta"),
    TELUGU("Telugu (తెలుగు)", "te"),
}

enum class StampTemplate(override val label: String) : Labeled {
    STANDARD("Standard (All Fields)"),
    INSPECTION("Field Inspection & Proof of Work"),
    ATTENDANCE("Selfie Attendance Punch"),
    MINIMAL("Minimal (GPS & Time Only)"),
}

enum class WatermarkStyle(override val label: String) : Labeled {
    CLASSIC_GEO_BADGE("Classic GeoData Card"),
    STUDIO_WHITE_BAR("Studio White Bottom Bar"),
    ACCENT_BLOCK_BAR("Yellow Accent Block Bar"),
    ACCENT_STRIPE_BAR("Yellow Stripe Dark Bar"),
    AVATAR_WHITE_BAR("Avatar White Bottom Bar"),
    EXHIBITION_WHITE_MAT("Exhibition White Mat Frame"),
    FILM_STRIP_35MM("35mm Negative Film Strip"),
    SIGNATURE_YELLOW_MAT("Signature Yellow Mat"),
    LUXURY_BLACK_MAT("Luxury Black Matte Frame"),
    RETRO_VINTAGE_FILM("Retro Vintage Film Frame"),
    EDITORIAL_TYPOGRAPHY("Editorial Multi-Line"),
    POLAROID_SIDEBAR("Polaroid Right Sidebar"),
}

/** Immutable copy of the settings, safe to hand to background rendering. */
data class StampConfig(
    val enabled: Set<StampField>,
    val note: String,
    val personName: String,
    val phone: String,
    val siteName: String = "",
    val custom1Label: String = "",
    val custom1Value: String = "",
    val custom2Label: String = "",
    val custom2Value: String = "",
    val number: Int,
    val mapType: MapType,
    val font: FontStyle,
    val color: StampColor,
    val background: StampBackground,
    val size: StampSize,
    val position: StampPosition,
    val mapPosition: MapPosition,
    val mapScalePercent: Int,
    val dateFormat: DateFormat,
    val coordinateFormat: CoordinateFormat = CoordinateFormat.DECIMAL,
    val language: StampLanguage = StampLanguage.ENGLISH,
    val logo: Bitmap?,
    val watermarkStyle: WatermarkStyle = WatermarkStyle.CLASSIC_GEO_BADGE,
    val deviceBrand: String = "POCO X7",
    val deviceBadge: String = "Pro",
    val customMotto: String = "Always believe that something wonderful is about to happen",
    val showExifParams: Boolean = true,
    val showGpsOnFrame: Boolean = true,
    val showAvatarOnFrame: Boolean = true,
) {
    fun has(f: StampField) = f in enabled
}

/** Settings stored in SharedPreferences and exposed as Compose state. */
class StampSettings(private val context: Context) {
    private val prefs = context.getSharedPreferences("stamp_settings", Context.MODE_PRIVATE)

    private val enabledMap = mutableStateMapOf<StampField, Boolean>().apply {
        StampField.entries.forEach { put(it, prefs.getBoolean(it.key, it.defaultOn)) }
    }

    fun isEnabled(field: StampField): Boolean = enabledMap[field] ?: field.defaultOn

    fun setEnabled(field: StampField, on: Boolean) {
        enabledMap[field] = on
        prefs.edit().putBoolean(field.key, on).apply()
    }

    // ---- text values ----
    private val noteState = stringState("note", "Note : Captured by GeoStamp Camera")
    var note: String
        get() = noteState.value
        set(v) { noteState.value = v; prefs.edit().putString("note", v).apply() }

    private val personState = stringState("person", "")
    var personName: String
        get() = personState.value
        set(v) { personState.value = v; prefs.edit().putString("person", v).apply() }

    private val phoneState = stringState("phone", "")
    var phone: String
        get() = phoneState.value
        set(v) { phoneState.value = v; prefs.edit().putString("phone", v).apply() }

    private val siteState = stringState("site_name", "")
    var siteName: String
        get() = siteState.value
        set(v) { siteState.value = v; prefs.edit().putString("site_name", v).apply() }

    private val custom1LabelState = stringState("custom1_label", "Engineer")
    var custom1Label: String
        get() = custom1LabelState.value
        set(v) { custom1LabelState.value = v; prefs.edit().putString("custom1_label", v).apply() }

    private val custom1ValueState = stringState("custom1_val", "")
    var custom1Value: String
        get() = custom1ValueState.value
        set(v) { custom1ValueState.value = v; prefs.edit().putString("custom1_val", v).apply() }

    private val custom2LabelState = stringState("custom2_label", "Contractor")
    var custom2Label: String
        get() = custom2LabelState.value
        set(v) { custom2LabelState.value = v; prefs.edit().putString("custom2_label", v).apply() }

    private val custom2ValueState = stringState("custom2_val", "")
    var custom2Value: String
        get() = custom2ValueState.value
        set(v) { custom2ValueState.value = v; prefs.edit().putString("custom2_val", v).apply() }

    private val languageState = enumState("language", StampLanguage.ENGLISH, StampLanguage.entries)
    var language: StampLanguage
        get() = languageState.value
        set(v) = putEnum("language", languageState, v)

    // Geofencing coordinates and radius
    private val siteLatState = mutableStateOf(if (prefs.contains("site_lat")) prefs.getFloat("site_lat", 0f).toDouble() else null)
    var siteLat: Double?
        get() = siteLatState.value
        set(v) {
            siteLatState.value = v
            if (v != null) prefs.edit().putFloat("site_lat", v.toFloat()).apply()
            else prefs.edit().remove("site_lat").apply()
        }

    private val siteLonState = mutableStateOf(if (prefs.contains("site_lon")) prefs.getFloat("site_lon", 0f).toDouble() else null)
    var siteLon: Double?
        get() = siteLonState.value
        set(v) {
            siteLonState.value = v
            if (v != null) prefs.edit().putFloat("site_lon", v.toFloat()).apply()
            else prefs.edit().remove("site_lon").apply()
        }

    private val siteRadiusMState = mutableStateOf(prefs.getFloat("site_radius_m", 250f))
    var siteRadiusM: Float
        get() = siteRadiusMState.value
        set(v) { siteRadiusMState.value = v; prefs.edit().putFloat("site_radius_m", v).apply() }

    fun applyTemplate(t: StampTemplate) {
        when (t) {
            StampTemplate.STANDARD -> {
                StampField.entries.forEach { setEnabled(it, it.defaultOn) }
            }
            StampTemplate.INSPECTION -> {
                StampField.entries.forEach { setEnabled(it, false) }
                listOf(
                    StampField.MAP, StampField.TITLE, StampField.ADDRESS, StampField.LAT_LONG,
                    StampField.ALTITUDE, StampField.PLUS_CODE, StampField.DATE_TIME, StampField.NUMBERING,
                    StampField.PROJECT, StampField.NOTE, StampField.QR_CODE, StampField.CUSTOM_1
                ).forEach { setEnabled(it, true) }
            }
            StampTemplate.ATTENDANCE -> {
                StampField.entries.forEach { setEnabled(it, false) }
                listOf(
                    StampField.TITLE, StampField.ADDRESS, StampField.LAT_LONG, StampField.DATE_TIME,
                    StampField.TIME_ZONE, StampField.PERSON_NAME, StampField.QR_CODE, StampField.PROJECT
                ).forEach { setEnabled(it, true) }
            }
            StampTemplate.MINIMAL -> {
                StampField.entries.forEach { setEnabled(it, false) }
                listOf(StampField.LAT_LONG, StampField.DATE_TIME, StampField.PLUS_CODE).forEach { setEnabled(it, true) }
            }
        }
    }

    fun saveCustomTemplate(slot: Int, name: String) {
        val set = StampField.entries.filter { isEnabled(it) }.map { it.name }.toSet()
        prefs.edit()
            .putStringSet("custom_template_fields_$slot", set)
            .putString("custom_template_name_$slot", name)
            .putString("custom_template_font_$slot", font.name)
            .putString("custom_template_color_$slot", color.name)
            .putString("custom_template_bg_$slot", background.name)
            .putString("custom_template_pos_$slot", position.name)
            .apply()
    }

    fun hasCustomTemplate(slot: Int): Boolean {
        return prefs.contains("custom_template_name_$slot")
    }

    fun getCustomTemplateName(slot: Int): String {
        return prefs.getString("custom_template_name_$slot", "Custom Template $slot") ?: "Custom Template $slot"
    }

    fun loadCustomTemplate(slot: Int) {
        val set = prefs.getStringSet("custom_template_fields_$slot", null) ?: return
        StampField.entries.forEach { setEnabled(it, it.name in set) }
        prefs.getString("custom_template_font_$slot", null)?.let { fname ->
            FontStyle.entries.firstOrNull { it.name == fname }?.let { font = it }
        }
        prefs.getString("custom_template_color_$slot", null)?.let { cname ->
            StampColor.entries.firstOrNull { it.name == cname }?.let { color = it }
        }
        prefs.getString("custom_template_bg_$slot", null)?.let { bname ->
            StampBackground.entries.firstOrNull { it.name == bname }?.let { background = it }
        }
        prefs.getString("custom_template_pos_$slot", null)?.let { pname ->
            StampPosition.entries.firstOrNull { it.name == pname }?.let { position = it }
        }
    }

    /** The number that will be stamped on the NEXT photo. */
    private val numberState = mutableStateOf(prefs.getInt("number", 1))
    var nextNumber: Int
        get() = numberState.value
        set(v) { numberState.value = v; prefs.edit().putInt("number", v).apply() }

    private val mapScaleState = mutableStateOf(prefs.getInt("map_scale", 85))
    var mapScalePercent: Int
        get() = mapScaleState.value
        set(v) { mapScaleState.value = v.coerceIn(40, 100); prefs.edit().putInt("map_scale", mapScaleState.value).apply() }

    // ---- choice values ----
    private val mapTypeState = enumState("map_type", MapType.SATELLITE, MapType.entries)
    var mapType: MapType
        get() = mapTypeState.value
        set(v) = putEnum("map_type", mapTypeState, v)

    private val fontState = enumState("font", FontStyle.SANS, FontStyle.entries)
    var font: FontStyle
        get() = fontState.value
        set(v) = putEnum("font", fontState, v)

    private val colorState = enumState("color", StampColor.MULTIPLE, StampColor.entries)
    var color: StampColor
        get() = colorState.value
        set(v) = putEnum("color", colorState, v)

    private val bgState = enumState("background", StampBackground.DARK, StampBackground.entries)
    var background: StampBackground
        get() = bgState.value
        set(v) = putEnum("background", bgState, v)

    private val sizeState = enumState("size", StampSize.MEDIUM, StampSize.entries)
    var size: StampSize
        get() = sizeState.value
        set(v) = putEnum("size", sizeState, v)

    private val positionState = enumState("position", StampPosition.BOTTOM, StampPosition.entries)
    var position: StampPosition
        get() = positionState.value
        set(v) = putEnum("position", positionState, v)

    private val mapPosState = enumState("map_position", MapPosition.LEFT, MapPosition.entries)
    var mapPosition: MapPosition
        get() = mapPosState.value
        set(v) = putEnum("map_position", mapPosState, v)

    private val dateFormatState = enumState("date_format", DateFormat.LONG, DateFormat.entries)
    var dateFormat: DateFormat
        get() = dateFormatState.value
        set(v) = putEnum("date_format", dateFormatState, v)

    private val coordFormatState = enumState("coord_format", CoordinateFormat.DECIMAL, CoordinateFormat.entries)
    var coordinateFormat: CoordinateFormat
        get() = coordFormatState.value
        set(v) = putEnum("coord_format", coordFormatState, v)

    // ---- logo ----
    private val logoFile get() = File(context.filesDir, "custom_logo.png")
    private val logoState = mutableStateOf(loadLogo())
    val logo: Bitmap? get() = logoState.value
    private val customLogoState = mutableStateOf(logoFile.exists())
    val hasCustomLogo: Boolean get() = customLogoState.value

    /** Saves a picked image as the logo (scaled down to 256 px). */
    fun setCustomLogo(bitmap: Bitmap) {
        val maxSide = maxOf(bitmap.width, bitmap.height).coerceAtLeast(1)
        val scale = 256f / maxSide
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
        } else bitmap
        logoFile.outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
        customLogoState.value = true
        logoState.value = loadLogo()
    }

    fun resetLogo() {
        logoFile.delete()
        customLogoState.value = false
        logoState.value = loadLogo()
    }

    private fun loadLogo(): Bitmap? {
        if (logoFile.exists()) {
            BitmapFactory.decodeFile(logoFile.absolutePath)?.let { return it }
        }
        val d = ContextCompat.getDrawable(context, R.drawable.ic_stamp_logo) ?: return null
        val bmp = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        d.setBounds(0, 0, 192, 192)
        d.draw(c)
        return bmp
    }

    private val watermarkStyleState = enumState("watermark_style", WatermarkStyle.CLASSIC_GEO_BADGE, WatermarkStyle.entries)
    var watermarkStyle: WatermarkStyle
        get() = watermarkStyleState.value
        set(v) = putEnum("watermark_style", watermarkStyleState, v)

    private val deviceBrandState = mutableStateOf(prefs.getString("device_brand", android.os.Build.MODEL ?: "POCO X7") ?: "POCO X7")
    var deviceBrand: String
        get() = deviceBrandState.value
        set(v) { deviceBrandState.value = v; prefs.edit().putString("device_brand", v).apply() }

    private val deviceBadgeState = mutableStateOf(prefs.getString("device_badge", "Pro") ?: "Pro")
    var deviceBadge: String
        get() = deviceBadgeState.value
        set(v) { deviceBadgeState.value = v; prefs.edit().putString("device_badge", v).apply() }

    private val customMottoState = mutableStateOf(prefs.getString("custom_motto", "Always believe that something wonderful is about to happen") ?: "Always believe that something wonderful is about to happen")
    var customMotto: String
        get() = customMottoState.value
        set(v) { customMottoState.value = v; prefs.edit().putString("custom_motto", v).apply() }

    private val showExifParamsState = mutableStateOf(prefs.getBoolean("show_exif_params", true))
    var showExifParams: Boolean
        get() = showExifParamsState.value
        set(v) { showExifParamsState.value = v; prefs.edit().putBoolean("show_exif_params", v).apply() }

    private val showGpsOnFrameState = mutableStateOf(prefs.getBoolean("show_gps_on_frame", true))
    var showGpsOnFrame: Boolean
        get() = showGpsOnFrameState.value
        set(v) { showGpsOnFrameState.value = v; prefs.edit().putBoolean("show_gps_on_frame", v).apply() }

    private val showAvatarOnFrameState = mutableStateOf(prefs.getBoolean("show_avatar_on_frame", true))
    var showAvatarOnFrame: Boolean
        get() = showAvatarOnFrameState.value
        set(v) { showAvatarOnFrameState.value = v; prefs.edit().putBoolean("show_avatar_on_frame", v).apply() }

    fun snapshot(): StampConfig = StampConfig(
        enabled = StampField.entries.filter { isEnabled(it) }.toSet(),
        note = note,
        personName = personName,
        phone = phone,
        siteName = siteName,
        custom1Label = custom1Label,
        custom1Value = custom1Value,
        custom2Label = custom2Label,
        custom2Value = custom2Value,
        number = nextNumber,
        mapType = mapType,
        font = font,
        color = color,
        background = background,
        size = size,
        position = position,
        mapPosition = mapPosition,
        mapScalePercent = mapScalePercent,
        dateFormat = dateFormat,
        coordinateFormat = coordinateFormat,
        language = language,
        logo = logo,
        watermarkStyle = watermarkStyle,
        deviceBrand = deviceBrand,
        deviceBadge = deviceBadge,
        customMotto = customMotto,
        showExifParams = showExifParams,
        showGpsOnFrame = showGpsOnFrame,
        showAvatarOnFrame = showAvatarOnFrame,
    )

    // ---- helpers ----
    private fun stringState(key: String, def: String) = mutableStateOf(prefs.getString(key, def) ?: def)

    private fun <E : Enum<E>> enumState(key: String, def: E, values: List<E>): MutableState<E> {
        val stored = prefs.getString(key, null)
        return mutableStateOf(values.firstOrNull { it.name == stored } ?: def)
    }

    private fun <E : Enum<E>> putEnum(key: String, state: MutableState<E>, value: E) {
        state.value = value
        prefs.edit().putString(key, value.name).apply()
    }
}
