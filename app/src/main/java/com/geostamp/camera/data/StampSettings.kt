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
    NOTE("f_note", "Note / Hashtag", StampGroup.BRANDING, true),
    PERSON_NAME("f_person", "Person name", StampGroup.BRANDING, true),
    PHONE("f_phone", "Contact number", StampGroup.BRANDING, true),

    TEMPERATURE("f_temp", "Temperature", StampGroup.WEATHER, true),
    WIND("f_wind", "Wind", StampGroup.WEATHER, true),
    HUMIDITY("f_humidity", "Humidity", StampGroup.WEATHER, true),
    PRESSURE("f_pressure", "Pressure", StampGroup.WEATHER, true),

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

enum class StampPosition(override val label: String) : Labeled { BOTTOM("Bottom"), TOP("Top") }

enum class MapPosition(override val label: String) : Labeled { LEFT("Left"), RIGHT("Right") }

enum class DateFormat(override val label: String, val pattern: String) : Labeled {
    LONG("Saturday, 17/10/2026 11:14 PM", "EEEE, dd/MM/yyyy hh:mm a"),
    DMY_24("17/10/2026 23:14", "dd/MM/yyyy HH:mm"),
    MDY("10/17/2026 11:14 PM", "MM/dd/yyyy hh:mm a"),
    MONTH_NAME("17 Oct 2026, 11:14:05 PM", "dd MMM yyyy, hh:mm:ss a"),
    ISO("2026-10-17 23:14:05", "yyyy-MM-dd HH:mm:ss"),
}

/** Immutable copy of the settings, safe to hand to background rendering. */
data class StampConfig(
    val enabled: Set<StampField>,
    val note: String,
    val personName: String,
    val phone: String,
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
    val logo: Bitmap?,
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

    fun snapshot(): StampConfig = StampConfig(
        enabled = StampField.entries.filter { isEnabled(it) }.toSet(),
        note = note,
        personName = personName,
        phone = phone,
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
        logo = logo,
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
