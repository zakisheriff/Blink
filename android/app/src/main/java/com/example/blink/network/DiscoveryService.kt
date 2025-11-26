package com.example.blink.network

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.blink.data.Device
import com.google.gson.Gson
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class DiscoveryService(private val context: Context, private val socketManager: SocketManager) {
    private val TAG = "DiscoveryService"
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _discoveredDevices = MutableStateFlow<List<Device>>(emptyList())
    val discoveredDevices: StateFlow<List<Device>> = _discoveredDevices

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering

    private val deviceId: String = getDeviceId()
    private val deviceName: String = getDeviceName()

    private var broadcastJob: Job? = null
    private var cleanupJob: Job? = null

    init {
        setupCallbacks()
    }

    private fun setupCallbacks() {
        socketManager.onDiscoveryMessage = { message, ipAddress ->
            handleDiscoveryMessage(message, ipAddress)
        }
    }

    // MARK: - Device ID
    private fun getDeviceId(): String {
        val prefs = context.getSharedPreferences("blink_prefs", Context.MODE_PRIVATE)
        var id = prefs.getString("device_id", null)

        if (id == null) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString("device_id", id).apply()
        }

        return id
    }

    private fun getDeviceName(): String {
        return Build.MODEL ?: "Android Device"
    }

    // MARK: - Discovery
    fun startDiscovery() {
        if (_isDiscovering.value) return

        Log.d(TAG, "Starting discovery service...")
        _isDiscovering.value = true
        socketManager.startUDPListener()
        Log.d(TAG, "UDP listener started")

        // Start broadcasting
        broadcastJob =
                scope.launch {
                    try {
                        while (isActive) {
                            broadcastPresence()
                            delay(NetworkConstants.BROADCAST_INTERVAL)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Broadcast loop error", e)
                    }
                }

        // Start cleanup timer
        cleanupJob =
                scope.launch {
                    try {
                        while (isActive) {
                            delay(5000)
                            cleanupStaleDevices()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Cleanup loop error", e)
                    }
                }

        // Initial broadcast
        scope.launch {
            Log.d(TAG, "Sending initial broadcast...")
            broadcastPresence()
        }
    }

    fun stopDiscovery() {
        _isDiscovering.value = false
        broadcastJob?.cancel()
        cleanupJob?.cancel()
    }

    private fun broadcastPresence() {
        try {
            val message =
                    DiscoveryMessage(
                            deviceId = deviceId,
                            deviceName = deviceName,
                            port = NetworkConstants.TRANSFER_PORT
                    )

            // Get local network base IPs
            val baseIPs = getLocalNetworkBaseIPs()
            Log.d(TAG, "Broadcasting to base IPs: $baseIPs")

            for (baseIP in baseIPs) {
                for (i in 1..254) {
                    val address = "$baseIP.$i"
                    socketManager.sendUDPBroadcast(message, address)
                }
            }
            Log.d(TAG, "Broadcast sent to ${baseIPs.size * 254} addresses")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to broadcast presence", e)
        }
    }

    private fun getLocalNetworkBaseIPs(): List<String> {
        val baseIPs = mutableListOf<String>()

        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()

                // Skip loopback and inactive interfaces
                if (networkInterface.isLoopback || !networkInterface.isUp) continue

                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()

                    // Check for IPv4
                    if (address is java.net.Inet4Address && !address.isLoopbackAddress) {
                        val ip = address.hostAddress
                        if (ip != null) {
                            // Assume /24 subnet for simplicity
                            val baseIP = ip.substring(0, ip.lastIndexOf('.'))
                            baseIPs.add(baseIP)
                            Log.d(
                                    TAG,
                                    "Found active interface: ${networkInterface.displayName} ($ip)"
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get network interfaces", e)
        }

        if (baseIPs.isEmpty()) {
            baseIPs.add("192.168.1")
        }

        return baseIPs.distinct()
    }

    // MARK: - Code-Based Connection
    private var hostedCode: String? = null

    fun startHosting(code: String) {
        this.hostedCode = code
        socketManager.startUDPListener()
        Log.d(TAG, "Started hosting with code: $code")
    }

    fun connectWithCode(code: String) {
        socketManager.startUDPListener()

        val message =
                DiscoveryMessage(
                        deviceId = deviceId,
                        deviceName = deviceName,
                        port = NetworkConstants.TRANSFER_PORT,
                        code = code,
                        type = "query"
                )

        // Broadcast query
        scope.launch {
            val baseIPs = getLocalNetworkBaseIPs()
            for (baseIP in baseIPs) {
                for (i in 1..254) {
                    val address = "$baseIP.$i"
                    socketManager.sendUDPBroadcast(message, address)
                }
            }
        }
    }

    private fun handleDiscoveryMessage(message: DiscoveryMessage, ipAddress: String) {
        Log.d(
                TAG,
                "Received discovery message from $ipAddress: ${message.deviceName} type: ${message.type}"
        )

        // Don't add ourselves
        if (message.deviceId == deviceId) {
            return
        }

        // Handle Code Query
        if (message.type == "query") {
            if (message.code != null && message.code == hostedCode) {
                Log.d(TAG, "Received valid code query from ${message.deviceName}")
                // Send response
                val response =
                        DiscoveryMessage(
                                deviceId = deviceId,
                                deviceName = deviceName,
                                port = NetworkConstants.TRANSFER_PORT,
                                code = message.code,
                                type = "response"
                        )
                socketManager.sendUDPBroadcast(response, ipAddress)

                // Also add the querier to our list
                addOrUpdateDevice(message, ipAddress)
            }
            return
        }

        // Handle Code Response or Presence
        if (message.type == "response" || message.type == "presence") {
            addOrUpdateDevice(message, ipAddress)
        }
    }

    private fun addOrUpdateDevice(message: DiscoveryMessage, ipAddress: String) {
        scope.launch {
            val currentDevices = _discoveredDevices.value.toMutableList()
            val existingIndex = currentDevices.indexOfFirst { it.id == message.deviceId }

            if (existingIndex >= 0) {
                // Update last seen time
                currentDevices[existingIndex] =
                        currentDevices[existingIndex].copy(lastSeen = System.currentTimeMillis())
                Log.d(TAG, "Updated device: ${message.deviceName}")
            } else {
                // Add new device
                val device =
                        Device(
                                id = message.deviceId,
                                name = message.deviceName,
                                ipAddress = ipAddress,
                                port = message.port,
                                deviceType =
                                        if (message.deviceType == "android")
                                                Device.DeviceType.ANDROID
                                        else Device.DeviceType.MAC
                        )
                currentDevices.add(device)
                Log.d(TAG, "Added new device: ${message.deviceName} at $ipAddress")
            }

            _discoveredDevices.value = currentDevices
        }
    }

    private fun cleanupStaleDevices() {
        val now = System.currentTimeMillis()
        val timeout = NetworkConstants.DEVICE_TIMEOUT

        val currentDevices = _discoveredDevices.value
        val activeDevices = currentDevices.filter { device -> now - device.lastSeen <= timeout }

        if (activeDevices.size != currentDevices.size) {
            _discoveredDevices.value = activeDevices
        }
    }

    // MARK: - Manual Device Addition
    fun addDevice(ipAddress: String, port: Int, name: String) {
        val device =
                Device(
                        name = name,
                        ipAddress = ipAddress,
                        port = port,
                        deviceType = Device.DeviceType.MAC
                )

        val currentDevices = _discoveredDevices.value.toMutableList()
        if (!currentDevices.any { it.ipAddress == ipAddress }) {
            currentDevices.add(device)
            _discoveredDevices.value = currentDevices
        }
    }

    fun getDiagnostics(): String {
        val baseIPs = getLocalNetworkBaseIPs()
        val sb = StringBuilder()
        sb.append("Device ID: $deviceId\n")
        sb.append("Device Name: $deviceName\n")
        sb.append("Discovery Active: ${_isDiscovering.value}\n")
        sb.append("Base IPs: $baseIPs\n")
        sb.append("Discovered Devices: ${_discoveredDevices.value.size}\n")
        return sb.toString()
    }

    fun getLocalIpAddress(): String {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue

                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is java.net.Inet4Address && !address.isLoopbackAddress) {
                        return address.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get local IP", e)
        }
        return "127.0.0.1"
    }

    fun cleanup() {
        stopDiscovery()
        scope.cancel()
    }
}
