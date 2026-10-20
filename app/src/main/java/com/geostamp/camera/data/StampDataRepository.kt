package com.geostamp.camera.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.geostamp.camera.stamp.PlusCode
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Collects everything the live camera watermark needs: GPS, address, Plus Code, weather,
 * map thumbnail, compass heading, magnetic field and sound level.
 */
class StampDataRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) : SensorEventListener {

    private val _data = MutableStateFlow(StampData())
    val data: StateFlow<StampData> = _data

    private val fused = LocationServices.getFusedLocationProviderClient(context)
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private var lastGeocoded: Location? = null
    private var lastWeatherLoc: Location? = null
    private var lastWeatherTime = 0L
    private var lastMapLoc: Location? = null
    private var currentLocation: Location? = null
    private var mapType = MapType.SATELLITE
    private var running = false
    private var locationRunning = false
    private var soundWanted = false

    private var geoJob: Job? = null
    private var weatherJob: Job? = null
    private var mapJob: Job? = null
    private var soundJob: Job? = null
    private var mslJob: Job? = null

    /** Last known location, used by the gallery screen as a fallback for photos without GPS. */
    val lastLocation: Location? get() = currentLocation

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { onLocation(it) }
        }
    }

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    fun hasLocationPermission(): Boolean =
        granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    fun start() {
        if (running) return
        running = true
        registerSensors()
        startLocation()
        if (soundWanted) startSound()
    }

    /** Call again after the location permission is granted. */
    @SuppressLint("MissingPermission")
    fun startLocation() {
        if (!running || locationRunning || !hasLocationPermission()) return
        locationRunning = true
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000L)
            .setMinUpdateDistanceMeters(3f)
            .build()
        fused.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        fused.lastLocation.addOnSuccessListener { loc -> if (loc != null) onLocation(loc) }
    }

    fun stop() {
        if (!running) return
        running = false
        locationRunning = false
        fused.removeLocationUpdates(locationCallback)
        sensorManager.unregisterListener(this)
        soundJob?.cancel()
        soundJob = null
    }

    fun setMapType(type: MapType) {
        if (type == mapType) return
        mapType = type
        lastMapLoc = null
        currentLocation?.let { refreshMap(it) }
    }

    fun setSoundEnabled(on: Boolean) {
        soundWanted = on
        if (on && running) startSound()
        if (!on) {
            soundJob?.cancel()
            soundJob = null
            _data.update { it.copy(soundDb = null) }
        }
    }

    /** Force a fresh address, weather and map. */
    fun refreshAll() {
        lastGeocoded = null
        lastWeatherLoc = null
        lastMapLoc = null
        currentLocation?.let { onLocation(it) }
    }

    private fun onLocation(loc: Location) {
        currentLocation = loc
        val mslCapable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        _data.update {
            it.copy(
                latitude = loc.latitude,
                longitude = loc.longitude,
                // On Android 14+ the sea-level altitude is filled in below instead.
                altitude = if (!loc.hasAltitude()) null else if (mslCapable && it.altitude != null) it.altitude else loc.altitude,
                accuracyM = if (loc.hasAccuracy()) loc.accuracy else null,
                plusCode = PlusCode.encode(loc.latitude, loc.longitude),
            )
        }
        if (mslCapable && loc.hasAltitude()) updateMslAltitude(loc)

        if (lastGeocoded == null || lastGeocoded!!.distanceTo(loc) > 25f) {
            lastGeocoded = loc
            geocode(loc)
        }
        val now = SystemClock.elapsedRealtime()
        if (lastWeatherLoc == null || lastWeatherLoc!!.distanceTo(loc) > 1000f ||
            now - lastWeatherTime > 15 * 60 * 1000L
        ) {
            lastWeatherLoc = loc
            lastWeatherTime = now
            refreshWeather(loc)
        }
        if (lastMapLoc == null || lastMapLoc!!.distanceTo(loc) > 15f) {
            refreshMap(loc)
        }
    }

    /**
     * GPS altitude is height above the WGS84 ellipsoid, which differs from height above
     * sea level by up to ~100 m. Android 14+ ships a geoid model to convert it.
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun updateMslAltitude(loc: Location) {
        mslJob?.cancel()
        mslJob = scope.launch(Dispatchers.IO) {
            val msl = try {
                val copy = Location(loc)
                android.location.altitude.AltitudeConverter().addMslAltitudeToLocation(context, copy)
                if (copy.hasMslAltitude()) copy.mslAltitudeMeters else null
            } catch (e: Exception) {
                null
            }
            _data.update { it.copy(altitude = msl ?: loc.altitude) }
        }
    }

    private fun geocode(loc: Location) {
        geoJob?.cancel()
        geoJob = scope.launch(Dispatchers.IO) {
            val g = Geo.reverse(context, loc.latitude, loc.longitude)
            if (g == null) {
                lastGeocoded = null // try again on the next fix
                return@launch
            }
            _data.update {
                it.copy(title = g.title, shortAddress = g.shortAddress, address = g.address, countryCode = g.countryCode)
            }
        }
    }

    private fun refreshWeather(loc: Location) {
        weatherJob?.cancel()
        weatherJob = scope.launch(Dispatchers.IO) {
            val w = WeatherFetcher.fetch(loc.latitude, loc.longitude)
            if (w == null) {
                lastWeatherLoc = null
                return@launch
            }
            _data.update {
                it.copy(
                    temperatureC = w.temperatureC,
                    humidity = w.humidity,
                    windKmh = w.windKmh,
                    pressureHpa = w.pressureHpa,
                    aqi = w.aqi,
                    sunrise = w.sunrise,
                    sunset = w.sunset,
                )
            }
        }
    }

    private fun refreshMap(loc: Location) {
        lastMapLoc = loc
        val type = mapType
        mapJob?.cancel()
        mapJob = scope.launch(Dispatchers.IO) {
            val bmp = MapTileFetcher.fetch(loc.latitude, loc.longitude, type, context = context)
            if (bmp == null) {
                lastMapLoc = null
                return@launch
            }
            _data.update { it.copy(mapBitmap = bmp, mapType = type) }
        }
    }

    // ---------------- Sound level ----------------

    @SuppressLint("MissingPermission")
    private fun startSound() {
        if (soundJob?.isActive == true) return
        if (!granted(Manifest.permission.RECORD_AUDIO)) return
        soundJob = scope.launch(Dispatchers.IO) {
            val rate = 44100
            val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minBuf <= 0) return@launch
            val recorder = try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, max(minBuf, 8192),
                )
            } catch (e: Exception) {
                return@launch
            }
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                return@launch
            }
            val buffer = ShortArray(4096)
            var lastPush = 0L
            try {
                recorder.startRecording()
                while (isActive) {
                    val n = recorder.read(buffer, 0, buffer.size)
                    if (n <= 0) continue
                    var sum = 0.0
                    for (i in 0 until n) sum += buffer[i].toDouble() * buffer[i]
                    val rms = sqrt(sum / n)
                    // Approximate sound pressure level (uncalibrated phone microphone).
                    val db = (20.0 * log10(max(rms, 1.0))).toFloat()
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastPush > 500) {
                        lastPush = now
                        _data.update { it.copy(soundDb = db) }
                    }
                }
            } catch (e: Exception) {
                // microphone busy or revoked
            } finally {
                try { recorder.stop() } catch (_: Exception) {}
                recorder.release()
            }
        }
    }

    // ---------------- Compass & magnetic field ----------------

    private val rotationMatrix = FloatArray(9)
    private val remapped = FloatArray(9)
    private val orientation = FloatArray(3)
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var hasGravity = false
    private var useRotationVector = false
    private var lastSensorPush = 0L
    private var pendingAzimuth: Float? = null
    private var pendingMagnetic: Float? = null

    private fun registerSensors() {
        val rot = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val mag = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val acc = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        useRotationVector = rot != null
        rot?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        mag?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        if (!useRotationVector) acc?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                pendingAzimuth = cameraAzimuth()
            }
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, gravity, 0, 3)
                hasGravity = true
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                pendingMagnetic = sqrt(x * x + y * y + z * z)
                if (!useRotationVector && hasGravity &&
                    SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)
                ) {
                    pendingAzimuth = cameraAzimuth()
                }
            }
        }
        // Push at most twice a second so the UI doesn't redraw constantly.
        val now = SystemClock.elapsedRealtime()
        if (now - lastSensorPush > 500) {
            lastSensorPush = now
            val az = pendingAzimuth
            val mg = pendingMagnetic
            _data.update { it.copy(azimuthDeg = az ?: it.azimuthDeg, magneticUt = mg ?: it.magneticUt) }
        }
    }

    /** Heading of the back camera when the phone is held upright. */
    private fun cameraAzimuth(): Float {
        SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapped)
        SensorManager.getOrientation(remapped, orientation)
        val deg = Math.toDegrees(orientation[0].toDouble()).toFloat()
        return (deg + 360f) % 360f
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
