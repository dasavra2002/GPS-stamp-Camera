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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.geostamp.camera.camera.CameraController
import com.geostamp.camera.data.CameraSettings
import com.geostamp.camera.data.StampDataRepository
import com.geostamp.camera.data.StampField
import com.geostamp.camera.data.StampSettings
import com.geostamp.camera.ui.Accent
import com.geostamp.camera.ui.CameraScreen
import com.geostamp.camera.ui.GalleryScreen
import com.geostamp.camera.ui.GalleryState
import com.geostamp.camera.ui.SettingsScreen

private enum class Screen { CAMERA, SETTINGS, GALLERY }

class MainActivity : ComponentActivity() {
    private val shutterTrigger = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (CrashReporter.showIfCrashed(this) { showApp() }) return
        showApp()
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP) {
            shutterTrigger.tryEmit(Unit)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun showApp() {
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, secondary = Accent)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .safeDrawingPadding(),
                ) { App(shutterTrigger = shutterTrigger) }
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
private fun App(shutterTrigger: kotlinx.coroutines.flow.SharedFlow<Unit>? = null) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val settings = remember { StampSettings(context.applicationContext) }
    val cameraSettings = remember { CameraSettings(context.applicationContext) }
    val repo = remember { StampDataRepository(context.applicationContext, scope) }
    val controller = remember { CameraController(context) }
    val galleryState = remember { GalleryState(scope) }

    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    var cameraGranted by remember { mutableStateOf(granted(Manifest.permission.CAMERA)) }
    var asked by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf(Screen.CAMERA) }

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
                }
                Lifecycle.Event.ON_PAUSE -> repo.stop()
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
            shutterTrigger = shutterTrigger,
            onOpenSettings = { screen = Screen.SETTINGS },
            onOpenGallery = { screen = Screen.GALLERY },
        )
        Screen.SETTINGS -> SettingsScreen(settings, repo, cameraSettings, onBack = { screen = Screen.CAMERA })
        Screen.GALLERY -> GalleryScreen(galleryState, settings, repo, onBack = { screen = Screen.CAMERA })
    }
}

@Composable
private fun PermissionScreen(showSettings: Boolean, onRequest: () -> Unit, onGallery: () -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("GeoStamp Camera", color = Color.White, fontSize = 24.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            "Camera access is needed to take photos. Location is used for the address, map and weather on the stamp.",
            color = Color.LightGray, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black)) {
            Text("Allow access")
        }
        if (showSettings) {
            TextButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            }) { Text("Open app settings", color = Accent) }
        }
        TextButton(onClick = onGallery) { Text("Stamp gallery photos instead", color = Color.White) }
    }
}
