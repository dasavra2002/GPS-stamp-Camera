package com.geostamp.camera.ui

import android.Manifest
import android.content.res.Configuration
import android.view.OrientationEventListener
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
import com.geostamp.camera.ui.theme.CameraColors
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

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp > configuration.screenHeightDp
    // Height of the floating top bar in landscape, so overlays below it line up with its edge.
    val landscapeTopInset = if (vm.showToolsBar) 104.dp else 60.dp

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

    LaunchedEffect(data, config, vm.tick, vm.mode, vm.frameLongShort, vm.selected, isLandscape) {
        vm.renderStampPreview(config, isLandscape)
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

    if (isLandscape) {
        Row(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                CameraViewfinder(
                    vm, config, isLandscape = true, landscapeTopInset = landscapeTopInset,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxHeight()
                        .aspectRatio(vm.nativeRatio),
                )

                Box(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Brush.verticalGradient(listOf(CameraColors.ScrimStrong, Color.Transparent)))
                        .statusBarsPadding(),
                ) {
                    CameraTopBar(vm, onOpenSettings, startVoiceNote)
                }

                GeofenceSection(vm, data.latitude, data.longitude, isLandscape = true, landscapeTopInset = landscapeTopInset)

                if (vm.showProBar) {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = landscapeTopInset),
                    ) {
                        ProInfoBar(vm)
                    }
                }

                if (vm.openPanel != null) {
                    Box(Modifier.align(Alignment.BottomCenter)) { OpenControlPanel(vm) }
                }
            }

            LandscapeRightDeck(vm, onShutter)
        }
    } else {
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
                CameraTopBar(vm, onOpenSettings, startVoiceNote)
            }

            GeofenceSection(vm, data.latitude, data.longitude, isLandscape = false, landscapeTopInset = landscapeTopInset)

            if (vm.showProBar) ProInfoBar(vm)

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                CameraViewfinder(
                    vm, config, isLandscape = false, landscapeTopInset = landscapeTopInset,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .aspectRatio(1f / vm.nativeRatio),
                )

                if (vm.openPanel != null) {
                    Box(Modifier.align(Alignment.BottomCenter)) { OpenControlPanel(vm) }
                }
            }

            PortraitBottomDeck(vm, onShutter)
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
