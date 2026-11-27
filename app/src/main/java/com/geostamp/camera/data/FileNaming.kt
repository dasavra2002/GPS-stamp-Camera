package com.geostamp.camera.data

import com.geostamp.camera.stamp.PlusCode
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** A piece of information that can go into a saved photo's file name. */
enum class FileNameField(val key: String, override val label: String, val example: String) : Labeled {
    PREFIX("prefix", "Prefix text", "GeoStamp"),
    DATE_TIME("datetime", "Date & time", "20261127_093012_481"),
    DATE("date", "Date only", "2026-11-27"),
    LAT_LONG("latlong", "Coordinates (lat, long)", "12.97160N_77.59460E"),
    PLUS_CODE("pluscode", "Plus Code", "7J4VXHC8+R2"),
    ALTITUDE("altitude", "Altitude", "920m"),
    PLACE("place", "Place title", "Bengaluru-Karnataka-India"),
    SHORT_ADDRESS("short_address", "Short address", "MG-Road-Bengaluru"),
    COUNTRY("country", "Country code", "IN"),
    SITE("site", "Project / site", "Site-A"),
    PERSON("person", "Person name", "Asha"),
    CUSTOM_1("custom1", "Custom field 1 value", "Contractor-X"),
    NUMBER("number", "Photo number", "0042"),
}

enum class FileNameSeparator(override val label: String, val char: Char) : Labeled {
    UNDERSCORE("Underscore  _", '_'),
    DASH("Dash  -", '-'),
}

/** How saved photos are named: which fields, in which order, joined by [separator]. */
data class FileNaming(
    val fields: List<FileNameField>,
    val prefix: String,
    val separator: FileNameSeparator,
) {
    companion object {
        const val DEFAULT_PREFIX = "GeoStamp"

        /** Matches the names the app always used: GeoStamp_yyyyMMdd_HHmmss_SSS.jpg */
        val DEFAULT = FileNaming(listOf(FileNameField.PREFIX, FileNameField.DATE_TIME), DEFAULT_PREFIX, FileNameSeparator.UNDERSCORE)

        /** Stores the field order as "key,key,…"; unknown keys are skipped. */
        fun encodeFields(fields: List<FileNameField>): String = fields.joinToString(",") { it.key }

        fun decodeFields(s: String?): List<FileNameField>? =
            s?.split(',')?.mapNotNull { k -> FileNameField.entries.firstOrNull { it.key == k.trim() } }?.distinct()
    }
}

/** The values a file name is built from. Anything null is left out of the name. */
data class FileNameValues(
    val time: ZonedDateTime,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val plusCode: String? = null,
    val place: String? = null,
    val shortAddress: String? = null,
    val countryCode: String? = null,
    val site: String? = null,
    val person: String? = null,
    val custom1: String? = null,
    val number: Int? = null,
) {
    companion object {
        fun from(data: StampData, config: StampConfig, time: ZonedDateTime, number: Int) = FileNameValues(
            time = time,
            latitude = data.latitude,
            longitude = data.longitude,
            altitude = data.altitude,
            plusCode = data.plusCode,
            place = data.title,
            shortAddress = data.shortAddress,
            countryCode = data.countryCode,
            site = config.siteName,
            person = config.personName,
            custom1 = config.custom1Value,
            number = number,
        )
    }
}

/**
 * Builds safe, readable photo file names from a [FileNaming] template.
 * Names keep letters in any script, digits and `+ . - _`; everything else (spaces, commas,
 * slashes, `: * ? " < > |`) becomes `-`. The whole name stays well under the 255-byte limit.
 */
object FileNamer {
    private const val MAX_TOKEN_CHARS = 48
    private const val MAX_BASE_BYTES = 180

    /** The file name without extension. [prefixOverride] replaces the prefix text (e.g. timelapse "Frame"). */
    fun baseName(naming: FileNaming, v: FileNameValues, prefixOverride: String? = null): String {
        val sep = naming.separator.char
        val parts = naming.fields.mapNotNull { token(it, naming, v, sep, prefixOverride) }.filter { it.isNotEmpty() }
        val joined = parts.joinToString(sep.toString())
        val base = truncateUtf8(joined, MAX_BASE_BYTES).trimEnd('-', '_', '.', ' ')
        // Never produce an empty or hidden (".…") name.
        return base.trimStart('.').ifEmpty { DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS", Locale.US).format(v.time) }
    }

    fun fileName(naming: FileNaming, v: FileNameValues, prefixOverride: String? = null, extension: String = "jpg") =
        baseName(naming, v, prefixOverride) + "." + extension

    private fun token(f: FileNameField, naming: FileNaming, v: FileNameValues, sep: Char, prefixOverride: String?): String? = when (f) {
        FileNameField.PREFIX -> clean(prefixOverride ?: naming.prefix)
        FileNameField.DATE_TIME -> DateTimeFormatter.ofPattern("yyyyMMdd'$sep'HHmmss'$sep'SSS", Locale.US).format(v.time)
        FileNameField.DATE -> DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US).format(v.time)
        FileNameField.LAT_LONG -> if (v.latitude != null && v.longitude != null) coordinates(v.latitude, v.longitude, sep) else null
        FileNameField.PLUS_CODE -> clean(v.plusCode) ?: v.latitude?.let { la -> v.longitude?.let { lo -> PlusCode.encode(la, lo) } }
        FileNameField.ALTITUDE -> v.altitude?.let { "${it.roundToInt()}m" }
        FileNameField.PLACE -> clean(v.place)
        FileNameField.SHORT_ADDRESS -> clean(v.shortAddress)
        FileNameField.COUNTRY -> clean(v.countryCode)?.uppercase(Locale.US)
        FileNameField.SITE -> clean(v.site)
        FileNameField.PERSON -> clean(v.person)
        FileNameField.CUSTOM_1 -> clean(v.custom1)
        FileNameField.NUMBER -> v.number?.let { String.format(Locale.US, "%04d", it) }
    }

    /** 5 decimals is about 1 m; hemisphere letters avoid a leading "-" that reads like a separator. */
    fun coordinates(lat: Double, lon: Double, sep: Char = '_'): String {
        fun f(x: Double) = String.format(Locale.US, "%.5f", abs(x))
        val ns = if (lat < 0) "S" else "N"
        val ew = if (lon < 0) "W" else "E"
        return f(lat) + ns + sep + f(lon) + ew
    }

    /** Makes free text safe for a file name; null if nothing usable is left. */
    fun clean(s: String?): String? {
        if (s.isNullOrBlank()) return null
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val ok = Character.isLetterOrDigit(cp) || cp == '+'.code || cp == '.'.code || cp == '-'.code || cp == '_'.code ||
                Character.getType(cp) == Character.NON_SPACING_MARK.toInt() ||
                Character.getType(cp) == Character.COMBINING_SPACING_MARK.toInt()
            if (ok) sb.appendCodePoint(cp) else if (sb.isNotEmpty() && sb.last() != '-') sb.append('-')
            i += Character.charCount(cp)
        }
        val out = truncateChars(sb.toString().trim('-', '.', '_'), MAX_TOKEN_CHARS).trimEnd('-', '.', '_')
        return out.ifEmpty { null }
    }

    private fun truncateChars(s: String, max: Int): String {
        if (s.codePointCount(0, s.length) <= max) return s
        return s.substring(0, s.offsetByCodePoints(0, max))
    }

    private fun truncateUtf8(s: String, maxBytes: Int): String {
        var bytes = 0
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val n = when {
                cp < 0x80 -> 1
                cp < 0x800 -> 2
                cp < 0x10000 -> 3
                else -> 4
            }
            if (bytes + n > maxBytes) return s.substring(0, i)
            bytes += n
            i += Character.charCount(cp)
        }
        return s
    }
}
