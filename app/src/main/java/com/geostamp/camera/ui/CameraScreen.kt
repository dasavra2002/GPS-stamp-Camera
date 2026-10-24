package com.geostamp.camera.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.OrientationEventListener
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.geostamp.camera.VolumeKeyEvent
import com.geostamp.camera.camera.CameraController
import com.geostamp.camera.camera.CaptureMode
import com.geostamp.camera.data.CameraSettings
import com.geostamp.camera.data.GeofenceManager
import com.geostamp.camera.data.StampDataRepository
import com.geostamp.camera.data.StampSettings
import kotlinx.coroutines.flow.SharedFlow

@Composable
fun CameraScreen(
    settings: StampSettings,
    cam: CameraSettings,
    repo: StampDataRepository,
    controller: CameraController,
    geofenceManager: GeofenceManager? = null,
    volumeKeyTrigger: SharedFlow<VolumeKeyEvent>? = null,
    onOpenSettings: () -> Unit,
    onOpenGallery: () -> Unit,
) {
    val context = LocalContext.current
    val vm = viewModel(viewModelStoreOwner = rememberScreenViewModelStoreOwner()) {
        CameraViewModel(context, settings, cam, repo, controller, geofenceManager ?: GeofenceManager(context))
    }
    val owner = LocalLifecycleOwner.current
    val data by repo.data.collectAsState()
    val config = settings.snapshot()

    // The screen stays portrait; icons turn with the phone instead (see CameraRotation.kt).
    LockPortrait()

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.micGranted = it }
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) vm.onVoiceNote(text)
        }
    }
    val startVoiceNote = {
        try {
            speechLauncher.launch(CameraIntents.voiceNoteIntent())
        } catch (_: Exception) {
            Toast.makeText(context, "Voice input not supported on this device", Toast.LENGTH_SHORT).show()
        }
    }
    val onShutter = { vm.onShutter(owner) }

    // Auto-switch project tags when entering geofence boundaries
    LaunchedEffect(data.latitude, data.longitude, vm.geofences.autoSwitchEnabled.value) {
        vm.autoSwitchSite(data.latitude, data.longitude)
    }

    // Ask for the microphone the first time video mode is opened (videos record silently without it).
    LaunchedEffect(vm.mode) {
        if (vm.mode == CaptureMode.VIDEO && !vm.micGranted) micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Bind the chosen lens with the chosen mode and settings.
    DisposableEffect(vm.lenses, vm.selected, vm.mode, vm.spec) {
        val release = vm.bindCamera(owner)
        onDispose { release() }
    }

    // Keep photos/videos upright when the phone is turned sideways.
    DisposableEffect(Unit) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation != ORIENTATION_UNKNOWN) vm.onDeviceRotation(orientation)
            }
        }
        listener.enable()
        onDispose { listener.disable() }
    }

    // Held sideways, photos come out landscape, so the stamp is drawn for a landscape frame.
    LaunchedEffect(data, config, vm.tick, vm.mode, vm.frameLongShort, vm.selected, vm.landscape) {
        vm.renderStampPreview(config, vm.landscape)
    }

    LaunchedEffect(volumeKeyTrigger) {
        volumeKeyTrigger?.collect { vm.onVolumeKey(it, owner) }
    }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is CameraEvent.UploadToDrive -> CameraIntents.uploadToGoogleDrive(context, event.uri, event.mime)
            }
        }
    }

    val iconRotation = rememberIconRotation(vm.deviceTurn)
    CompositionLocalProvider(LocalIconRotation provides iconRotation, LocalDeviceTurn provides vm.deviceTurn) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
            ) {
                CameraTopBar(vm, onOpenSettings)
            }

            GeofenceSection(vm, data.latitude, data.longitude)

            if (vm.showProBar) ProInfoBar(vm)

            // Viewfinder, with the quick-settings grid and the tools sheet opening over it.
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .aspectRatio(1f / vm.nativeRatio),
                ) {
                    CameraViewfinder(vm, config, modifier = Modifier.fillMaxSize())
                    if (vm.openPanel != null) {
                        Box(Modifier.align(Alignment.BottomCenter)) { OpenControlPanel(vm) }
                    }
                }

                CameraSheets(vm, onOpenSettings, onOpenGallery, startVoiceNote)
            }

            CameraBottomDeck(vm, onShutter)
        }
    }
}

@Composable
private fun ProInfoBar(vm: CameraViewModel) {
    val readout by vm.controller.readout.collectAsState()
    InfoBar(
        mode = vm.mode, manual = vm.manual, readout = readout, cam = vm.cam, sensor = vm.sensor,
        formatText = vm.formatText, open = vm.openPanel, enabled = true,
        onOpen = vm::requestPanel,
    )
}

/** The pro control panel picked in the info bar, if any. */
@Composable
private fun OpenControlPanel(vm: CameraViewModel) {
    val p = vm.openPanel ?: return
    val readout by vm.controller.readout.collectAsState()
    val evRange = vm.controller.camera?.cameraInfo?.exposureState?.let {
        if (it.isExposureCompensationSupported) it.exposureCompensationRange.lower..it.exposureCompensationRange.upper else null
    }
    val sensor = vm.sensor
    ControlPanel(
        panel = p, mode = vm.mode, manual = vm.manual, readout = readout, sensor = sensor, caps = vm.caps, cam = vm.cam,
        evRange = evRange,
        photoSizes = if (vm.cam.photoAspect.native16x9) sensor.photoSizes169 else sensor.photoSizes43,
    )
}

/** Keeps the activity in portrait while the camera is open, and gives rotation back to the sensor after. */
@Composable
private fun LockPortrait() {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity) {
        val before = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose { activity.requestedOrientation = before }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Scopes the camera ViewModel to this screen: it's created when the camera opens and cleared
 * (camera released, jobs cancelled) when the user goes to Settings or the gallery.
 */
@Composable
private fun rememberScreenViewModelStoreOwner(): ViewModelStoreOwner {
    val owner = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(owner) {
        onDispose { owner.viewModelStore.clear() }
    }
    return owner
}
