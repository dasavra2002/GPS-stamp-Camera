package com.geostamp.camera.location

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.geostamp.camera.data.StampDataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

enum class BluetoothGpsState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

data class BluetoothDeviceInfo(
    val name: String,
    val address: String,
)

class BluetoothGpsManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val repo: StampDataRepository? = null,
) {
    companion object {
        // Standard Bluetooth Serial Port Profile (SPP) UUID
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val adapter: BluetoothAdapter? = bluetoothManager?.adapter

    val connectionState = mutableStateOf(BluetoothGpsState.DISCONNECTED)
    val connectedDevice = mutableStateOf<BluetoothDeviceInfo?>(null)
    val lastLocation = mutableStateOf<NmeaLocation?>(null)
    val errorMessage = mutableStateOf<String?>(null)

    private var socket: BluetoothSocket? = null
    private var streamJob: Job? = null

    val isSupported: Boolean
        get() = adapter != null

    val isEnabled: Boolean
        get() = adapter?.isEnabled == true

    fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDeviceInfo> {
        if (!hasPermission() || adapter == null || !adapter.isEnabled) return emptyList()
        return try {
            adapter.bondedDevices?.map {
                BluetoothDeviceInfo(it.name ?: "Unknown Device", it.address)
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e("BluetoothGpsManager", "Error querying bonded devices", e)
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(
        deviceInfo: BluetoothDeviceInfo,
        onConnected: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (!hasPermission()) {
            val err = "Bluetooth permission not granted"
            errorMessage.value = err
            onError(err)
            return
        }

        disconnect()
        connectionState.value = BluetoothGpsState.CONNECTING
        errorMessage.value = null

        streamJob = scope.launch(Dispatchers.IO) {
            try {
                val device = adapter?.getRemoteDevice(deviceInfo.address)
                if (device == null) {
                    throw IllegalStateException("Device not found: ${deviceInfo.address}")
                }

                val sock = device.createRfcommSocketToServiceRecord(SPP_UUID)
                adapter.cancelDiscovery()
                sock.connect()
                socket = sock

                withContext(Dispatchers.Main) {
                    connectionState.value = BluetoothGpsState.CONNECTED
                    connectedDevice.value = deviceInfo
                    onConnected()
                }

                val reader = BufferedReader(InputStreamReader(sock.inputStream))
                var currentLoc = NmeaLocation()

                while (isActive && sock.isConnected) {
                    val line = reader.readLine() ?: break
                    val updated = NmeaParser.parseSentence(line, currentLoc)
                    if (updated != null) {
                        currentLoc = updated
                        withContext(Dispatchers.Main) {
                            lastLocation.value = currentLoc
                            if (currentLoc.isAccurate && currentLoc.latitude != null && currentLoc.longitude != null) {
                                repo?.updateExternalLocation(
                                    latitude = currentLoc.latitude!!,
                                    longitude = currentLoc.longitude!!,
                                    altitudeM = currentLoc.altitudeM,
                                    accuracyM = currentLoc.accuracyM,
                                    bearingDeg = currentLoc.bearingDeg
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("BluetoothGpsManager", "Connection failure or closed: ${e.message}")
                withContext(Dispatchers.Main) {
                    disconnect()
                    val err = e.localizedMessage ?: "Failed to connect to ${deviceInfo.name}"
                    errorMessage.value = err
                    onError(err)
                }
            }
        }
    }

    fun disconnect() {
        streamJob?.cancel()
        streamJob = null

        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null

        connectionState.value = BluetoothGpsState.DISCONNECTED
        connectedDevice.value = null
    }
}
