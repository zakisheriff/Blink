package com.example.blink.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.blink.data.Device
import com.example.blink.data.QRCodePayload
import com.example.blink.data.TransferItem
import com.example.blink.network.DiscoveryService
import com.example.blink.network.NetworkConstants
import com.example.blink.network.SocketManager
import com.example.blink.network.TransferManager
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BlinkViewModel(application: Application) : AndroidViewModel(application) {
    private val socketManager = SocketManager()
    private val discoveryService = DiscoveryService(application, socketManager)
    private val transferManager = TransferManager(application, socketManager)
    private val gson = Gson()

    val discoveredDevices = discoveryService.discoveredDevices
    val isDiscovering = discoveryService.isDiscovering
    val activeTransfers = transferManager.activeTransfers

    private val _selectedFiles = MutableStateFlow<List<TransferItem>>(emptyList())
    val selectedFiles: StateFlow<List<TransferItem>> = _selectedFiles

    private val _selectedDevice = MutableStateFlow<Device?>(null)
    val selectedDevice: StateFlow<Device?> = _selectedDevice

    private val _showQRCode = MutableStateFlow(false)
    val showQRCode: StateFlow<Boolean> = _showQRCode

    private val _showQRScanner = MutableStateFlow(false)
    val showQRScanner: StateFlow<Boolean> = _showQRScanner

    init {
        startServices()
    }

    private fun startServices() {
        discoveryService.startDiscovery()
        socketManager.startTCPServer()
    }

    fun selectDevice(device: Device) {
        _selectedDevice.value = device
    }

    fun addFiles(uris: List<Uri>) {
        val context = getApplication<Application>()
        val newItems =
                uris.mapNotNull { uri ->
                    try {
                        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val nameIndex =
                                        cursor.getColumnIndex(
                                                android.provider.OpenableColumns.DISPLAY_NAME
                                        )
                                val sizeIndex =
                                        cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)

                                val name =
                                        if (nameIndex >= 0) {
                                            cursor.getString(nameIndex)
                                                    ?: uri.lastPathSegment ?: "unknown"
                                        } else {
                                            uri.lastPathSegment ?: "unknown"
                                        }

                                val size =
                                        if (sizeIndex >= 0) {
                                            cursor.getLong(sizeIndex)
                                        } else {
                                            0L
                                        }

                                TransferItem(uri = uri.toString(), name = name, size = size)
                            } else {
                                // Fallback if cursor is empty
                                TransferItem(
                                        uri = uri.toString(),
                                        name = uri.lastPathSegment ?: "unknown",
                                        size = 0L
                                )
                            }
                        }
                                ?: run {
                                    // Fallback if query returns null
                                    TransferItem(
                                            uri = uri.toString(),
                                            name = uri.lastPathSegment ?: "unknown",
                                            size = 0L
                                    )
                                }
                    } catch (e: Exception) {
                        android.util.Log.e("BlinkViewModel", "Error reading file info", e)
                        // Return fallback item on error
                        TransferItem(
                                uri = uri.toString(),
                                name = uri.lastPathSegment ?: "unknown",
                                size = 0L
                        )
                    }
                }

        _selectedFiles.value = _selectedFiles.value + newItems
    }

    fun removeFile(item: TransferItem) {
        _selectedFiles.value = _selectedFiles.value.filter { it.id != item.id }
    }

    fun clearFiles() {
        _selectedFiles.value = emptyList()
    }

    fun sendFiles() {
        val device = _selectedDevice.value ?: return
        val files = _selectedFiles.value
        if (files.isEmpty()) return

        viewModelScope.launch {
            transferManager.sendFiles(files, device)
            clearFiles()
        }
    }

    fun cancelTransfer(transferId: String) {
        transferManager.cancelTransfer(transferId)
    }

    fun toggleQRCode() {
        _showQRCode.value = !_showQRCode.value
    }

    fun getQRCodePayload(): QRCodePayload {
        val prefs =
                getApplication<Application>()
                        .getSharedPreferences("blink_prefs", android.content.Context.MODE_PRIVATE)
        val deviceId = prefs.getString("device_id", java.util.UUID.randomUUID().toString()) ?: ""
        val deviceName = android.os.Build.MODEL ?: "Android Device"

        // Get local IP
        val ipAddress = discoveryService.getLocalIpAddress()

        return QRCodePayload(
                deviceId = deviceId,
                deviceName = deviceName,
                ipAddress = ipAddress,
                port = com.example.blink.network.NetworkConstants.TRANSFER_PORT
        )
    }

    fun getQRCodeJson(): String {
        return gson.toJson(getQRCodePayload())
    }

    fun getDiagnostics(): String {
        return discoveryService.getDiagnostics()
    }

    fun addManualDevice(ipAddress: String) {
        discoveryService.addDevice(
                ipAddress = ipAddress,
                port = NetworkConstants.TRANSFER_PORT,
                name = "Manual Device ($ipAddress)"
        )

        // Select it immediately
        val device =
                Device(
                        id = java.util.UUID.randomUUID().toString(),
                        name = "Manual Device ($ipAddress)",
                        ipAddress = ipAddress,
                        port = NetworkConstants.TRANSFER_PORT,
                        deviceType = Device.DeviceType.MAC
                )
        _selectedDevice.value = device
    }

    fun startHosting(code: String) {
        discoveryService.startHosting(code)
    }

    fun connectWithCode(code: String) {
        discoveryService.connectWithCode(code)
    }

    fun toggleQRScanner() {
        _showQRScanner.value = !_showQRScanner.value
    }

    fun handleQRCodeScanned(qrCodeJson: String) {
        try {
            val payload = gson.fromJson(qrCodeJson, QRCodePayload::class.java)

            // Create device from QR code
            val device =
                    Device(
                            id = payload.deviceId,
                            name = payload.deviceName,
                            ipAddress = payload.ipAddress,
                            port = payload.port,
                            deviceType = Device.DeviceType.MAC
                    )

            // Add to discovered devices
            discoveryService.addDevice(
                    ipAddress = payload.ipAddress,
                    port = payload.port,
                    name = payload.deviceName
            )

            // Select the device
            _selectedDevice.value = device

            // Close scanner
            _showQRScanner.value = false

            android.util.Log.d(
                    "BlinkViewModel",
                    "Device added from QR: ${device.name} at ${device.ipAddress}"
            )
        } catch (e: Exception) {
            android.util.Log.e("BlinkViewModel", "Failed to parse QR code", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        discoveryService.cleanup()
        transferManager.cleanup()
        socketManager.cleanup()
    }
}
