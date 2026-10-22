package com.geostamp.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.geostamp.camera.camera.CameraController
import com.geostamp.camera.data.CameraSettings
import com.geostamp.camera.data.GeofenceManager
import com.geostamp.camera.data.StampDataRepository
import com.geostamp.camera.location.BluetoothGpsManager
import com.geostamp.camera.data.StampField
import com.geostamp.camera.data.StampSettings
import com.geostamp.camera.data.VolumeButtonAction
import com.geostamp.camera.ui.CameraScreen
import com.geostamp.camera.ui.GalleryScreen
import com.geostamp.camera.ui.GalleryState
import com.geostamp.camera.ui.SettingsScreen
import com.geostamp.camera.ui.theme.GeoStampTheme

enum class VolumeKeyEvent { UP, DOWN }

private enum class Screen { CAMERA, SETTINGS, GALLERY }

class MainActivity : ComponentActivity() {
    private val volumeKeyTrigger = kotlinx.coroutines.flow.MutableSharedFlow<VolumeKeyEvent>(extraBufferCapacity = 1)
    private val currentIntent = mutableStateOf<Intent?>(null)

    companion object {
        const val ACTION_OPEN_CAMERA = "com.geostamp.camera.ACTION_OPEN_CAMERA"
        const val ACTION_PUNCH_IN = "com.geostamp.camera.ACTION_PUNCH_IN"
        const val ACTION_OPEN_GALLERY = "com.geostamp.camera.ACTION_OPEN_GALLERY"
        const val EXTRA_START_SCREEN = "extra_start_screen"
        const val EXTRA_START_MODE = "extra_start_mode"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        enableEdgeToEdge()
        currentIntent.value = intent
        if (CrashReporter.showIfCrashed(this) { showApp() }) return
        showApp()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        currentIntent.value = intent
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        val cam = CameraSettings(applicationContext)
        if (cam.volumeButtonAction == VolumeButtonAction.VOLUME) {
            return super.onKeyDown(keyCode, event)
        }
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP) {
            volumeKeyTrigger.tryEmit(VolumeKeyEvent.UP)
            return true
        }
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
            volumeKeyTrigger.tryEmit(VolumeKeyEvent.DOWN)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun showApp() {
        setContent {
            GeoStampTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    App(volumeKeyTrigger = volumeKeyTrigger, currentIntent = currentIntent)
                }
            }
        }
    }
}

private fun startupPermissions(): Array<String> {
    val list = mutableListOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) list += Manifest.permission.WRITE_EXTERNAL_STORAGE
    return list.toTypedArray()
}

@Composable
private fun App(
    volumeKeyTrigger: kotlinx.coroutines.flow.SharedFlow<VolumeKeyEvent>? = null,
    currentIntent: androidx.compose.runtime.State<Intent?>? = null,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val settings = remember { StampSettings(context.applicationContext) }
    val cameraSettings = remember { CameraSettings(context.applicationContext) }
    val repo = remember { StampDataRepository(context.applicationContext, scope) }
    val controller = remember { CameraController(context) }
    val galleryState = remember { GalleryState(scope) }
    val geofenceManager = remember { GeofenceManager(context.applicationContext) }
    val bluetoothGpsManager = remember { BluetoothGpsManager(context.applicationContext, scope, repo) }

    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    var cameraGranted by remember { mutableStateOf(granted(Manifest.permission.CAMERA)) }
    var asked by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf(Screen.CAMERA) }

    // React to incoming widget intents or launcher actions
    LaunchedEffect(currentIntent?.value) {
        val it = currentIntent?.value ?: return@LaunchedEffect
        val action = it.action
        val startScreen = it.getStringExtra(MainActivity.EXTRA_START_SCREEN)
        val startMode = it.getStringExtra(MainActivity.EXTRA_START_MODE)
        if (action == MainActivity.ACTION_PUNCH_IN || startMode == "ATTENDANCE") {
            screen = Screen.CAMERA
            cameraSettings.attendanceMode = true
        } else if (action == MainActivity.ACTION_OPEN_GALLERY || startScreen == "GALLERY") {
            screen = Screen.GALLERY
        } else if (action == MainActivity.ACTION_OPEN_CAMERA) {
            screen = Screen.CAMERA
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        cameraGranted = granted(Manifest.permission.CAMERA)
        asked = true
        repo.startLocation()
    }

    LaunchedEffect(Unit) {
        val missing = startupPermissions().filter { !granted(it) }
        if (missing.isNotEmpty()) launcher.launch(missing.toTypedArray()) else asked = true
    }

    // Run GPS/sensors only while the app is on screen.
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    cameraGranted = granted(Manifest.permission.CAMERA)
                    repo.start()
                    com.geostamp.camera.widget.GeoStampAppWidgetProvider.updateAllWidgets(context)
                }
                Lifecycle.Event.ON_PAUSE -> {
                    repo.stop()
                    com.geostamp.camera.widget.GeoStampAppWidgetProvider.updateAllWidgets(context)
                }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            repo.stop()
            controller.shutdown()
        }
    }

    // Keep background data in step with the settings.
    LaunchedEffect(settings.mapType) { repo.setMapType(settings.mapType) }
    val soundOn = settings.isEnabled(StampField.SOUND_LEVEL)
    LaunchedEffect(soundOn) { repo.setSoundEnabled(soundOn) }

    if (!cameraGranted) {
        PermissionScreen(
            showSettings = asked,
            onRequest = { launcher.launch(startupPermissions()) },
            onGallery = { screen = Screen.GALLERY },
        )
        if (screen == Screen.GALLERY) {
            GalleryScreen(galleryState, settings, repo, onBack = { screen = Screen.CAMERA })
        }
        return
    }

    when (screen) {
        Screen.CAMERA -> CameraScreen(
            settings = settings,
            cam = cameraSettings,
            repo = repo,
            controller = controller,
            geofenceManager = geofenceManager,
            volumeKeyTrigger = volumeKeyTrigger,
            onOpenSettings = { screen = Screen.SETTINGS },
            onOpenGallery = { screen = Screen.GALLERY },
        )
        Screen.SETTINGS -> SettingsScreen(
            settings = settings,
            repo = repo,
            cam = cameraSettings,
            geofenceManager = geofenceManager,
            bluetoothGpsManager = bluetoothGpsManager,
            onBack = { screen = Screen.CAMERA }
        )
        Screen.GALLERY -> GalleryScreen(galleryState, settings, repo, onBack = { screen = Screen.CAMERA })
    }
}

@Composable
private fun PermissionScreen(showSettings: Boolean, onRequest: () -> Unit, onGallery: () -> Unit) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Filled.Camera,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                    Text(
                        "GeoStamp Camera",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Camera access is needed to capture photos and video. Location access enables precise coordinates, address, map tiles, altitude, and weather stamping.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(28.dp))
                    Button(
                        onClick = onRequest,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text("Grant Permissions", style = MaterialTheme.typography.labelLarge)
                    }
                    if (showSettings) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Open App Settings")
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            OutlinedButton(
                onClick = onGallery,
                shape = MaterialTheme.shapes.medium,
            ) {
                Text("Stamp Gallery Photos Instead")
            }
        }
    }
}
