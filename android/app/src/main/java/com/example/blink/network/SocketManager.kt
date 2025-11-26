package com.example.blink.network

import android.util.Log
import com.google.gson.Gson
import java.net.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SocketManager {
    private val TAG = "SocketManager"
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var udpSocket: DatagramSocket? = null
    private var tcpServerSocket: ServerSocket? = null
    private val connections = ConcurrentHashMap<String, Socket>()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // Callbacks
    var onDiscoveryMessage: ((DiscoveryMessage, String) -> Unit)? = null
    var onConnectionReceived: ((Socket) -> Unit)? = null
    var onDataReceived: ((ByteArray, String) -> Unit)? = null

    // MARK: - UDP Broadcast
    fun startUDPListener(port: Int = NetworkConstants.DISCOVERY_PORT) {
        scope.launch {
            try {
                udpSocket =
                        DatagramSocket(port).apply {
                            broadcast = true
                            reuseAddress = true
                        }

                _isListening.value = true
                Log.d(TAG, "UDP listener started on port $port")

                val buffer = ByteArray(1024)
                while (isActive && udpSocket?.isClosed == false) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        udpSocket?.receive(packet)

                        val data = packet.data.copyOf(packet.length)
                        val message = String(data)
                        val ipAddress = packet.address.hostAddress ?: continue

                        try {
                            val discoveryMsg = gson.fromJson(message, DiscoveryMessage::class.java)
                            onDiscoveryMessage?.invoke(discoveryMsg, ipAddress)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse discovery message", e)
                        }
                    } catch (e: SocketException) {
                        if (isActive) {
                            Log.e(TAG, "UDP receive error", e)
                        }
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "UDP listener failed", e)
                _error.value = "UDP listener failed: ${e.message}"
                _isListening.value = false
            }
        }
    }

    fun sendUDPBroadcast(
            message: DiscoveryMessage,
            address: String,
            port: Int = NetworkConstants.DISCOVERY_PORT
    ) {
        scope.launch {
            try {
                val json = gson.toJson(message)
                val data = json.toByteArray()

                val socket = DatagramSocket().apply { broadcast = true }

                val packet = DatagramPacket(data, data.size, InetAddress.getByName(address), port)

                socket.send(packet)
                socket.close()
            } catch (e: Exception) {
                Log.e(TAG, "UDP send error", e)
            }
        }
    }

    // MARK: - TCP Server
    fun startTCPServer(port: Int = NetworkConstants.TRANSFER_PORT) {
        scope.launch {
            try {
                tcpServerSocket = ServerSocket(port).apply { reuseAddress = true }

                Log.d(TAG, "TCP server started on port $port")

                while (isActive && tcpServerSocket?.isClosed == false) {
                    try {
                        val socket = tcpServerSocket?.accept() ?: break
                        Log.d(TAG, "New TCP connection from ${socket.inetAddress.hostAddress}")

                        val connectionId = "${socket.inetAddress.hostAddress}:${socket.port}"
                        connections[connectionId] = socket

                        onConnectionReceived?.invoke(socket)

                        // Start receiving data
                        launch { receiveData(socket, connectionId) }
                    } catch (e: SocketException) {
                        if (isActive) {
                            Log.e(TAG, "TCP accept error", e)
                        }
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "TCP server failed", e)
                _error.value = "TCP server failed: ${e.message}"
            }
        }
    }

    private suspend fun receiveData(socket: Socket, connectionId: String) {
        try {
            val inputStream = socket.getInputStream()
            val buffer = ByteArray(NetworkConstants.BUFFER_SIZE)

            while (coroutineContext.isActive && !socket.isClosed) {
                val bytesRead = inputStream.read(buffer)
                if (bytesRead == -1) break

                val data = buffer.copyOf(bytesRead)
                onDataReceived?.invoke(data, connectionId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "TCP receive error", e)
        } finally {
            connections.remove(connectionId)
            socket.close()
        }
    }

    // MARK: - TCP Client
    suspend fun connectToDevice(ipAddress: String, port: Int): Socket? {
        return withContext(Dispatchers.IO) {
            try {
                val socket = Socket(ipAddress, port)
                val connectionId = "${socket.inetAddress.hostAddress}:${socket.port}"
                connections[connectionId] = socket

                // Start receiving data
                launch { receiveData(socket, connectionId) }

                socket
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to device", e)
                null
            }
        }
    }

    suspend fun sendData(data: ByteArray, socket: Socket): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                socket.getOutputStream().write(data)
                socket.getOutputStream().flush()
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send data", e)
                false
            }
        }
    }

    // MARK: - Cleanup
    fun stop() {
        scope.launch {
            udpSocket?.close()
            tcpServerSocket?.close()

            connections.values.forEach { it.close() }
            connections.clear()

            _isListening.value = false
        }
    }

    fun cleanup() {
        stop()
        scope.cancel()
    }
}
