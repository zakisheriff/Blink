package com.example.blink.network

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.blink.data.Device
import com.example.blink.data.TransferItem
import com.example.blink.data.TransferProgress
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream
import java.net.Socket
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TransferManager(private val context: Context, private val socketManager: SocketManager) {
    private val TAG = "TransferManager"
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _activeTransfers = MutableStateFlow<List<TransferProgress>>(emptyList())
    val activeTransfers: StateFlow<List<TransferProgress>> = _activeTransfers

    private val connections = mutableMapOf<String, Socket>()
    private val transferBuffers = mutableMapOf<String, ByteArray>()

    init {
        setupCallbacks()
    }

    private fun setupCallbacks() {
        socketManager.onDataReceived = { data, connectionId ->
            handleReceivedData(data, connectionId)
        }
    }

    // MARK: - Send Files
    suspend fun sendFiles(items: List<TransferItem>, device: Device) {
        withContext(Dispatchers.IO) {
            try {
                // Connect to device
                val socket =
                        socketManager.connectToDevice(device.ipAddress, device.port)
                                ?: throw Exception("Failed to connect to device")

                connections[device.id] = socket

                // Create transfers
                for (item in items) {
                    val transfer = TransferProgress(item = item, device = device)

                    addTransfer(transfer)

                    // Start transfer
                    launch { performTransfer(transfer, socket) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send files", e)
            }
        }
    }

    private suspend fun performTransfer(transfer: TransferProgress, socket: Socket) {
        try {
            updateTransferStatus(transfer.id, TransferProgress.TransferStatus.TRANSFERRING)

            // Send transfer request
            sendTransferRequest(transfer, socket)

            // Wait for acceptance (simplified - in real app, wait for response)
            delay(100)

            // Send file data
            sendFileData(transfer, socket)

            updateTransferStatus(transfer.id, TransferProgress.TransferStatus.COMPLETED)
        } catch (e: Exception) {
            Log.e(TAG, "Transfer failed", e)
            updateTransferError(transfer.id, e.message ?: "Transfer failed")
        }
    }

    private suspend fun sendTransferRequest(transfer: TransferProgress, socket: Socket) {
        val request =
                TransferRequest(
                        transferId = transfer.id,
                        fileName = transfer.item.name,
                        fileSize = transfer.item.size,
                        isDirectory = transfer.item.isDirectory
                )

        val requestJson = gson.toJson(request)
        val message = ProtocolMessage(type = MessageType.TRANSFER_REQUEST, payload = requestJson)

        val messageJson = gson.toJson(message)
        socketManager.sendData(messageJson.toByteArray(), socket)
    }

    private suspend fun sendFileData(transfer: TransferProgress, socket: Socket) {
        val uri = Uri.parse(transfer.item.uri)
        val inputStream =
                context.contentResolver.openInputStream(uri)
                        ?: throw Exception("Failed to open file")

        var sequenceNumber = 0L
        val bufferSize = NetworkConstants.BUFFER_SIZE
        var totalSent = 0L
        val startTime = System.currentTimeMillis()

        inputStream.use { input ->
            val buffer = ByteArray(bufferSize)

            while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead == -1) break

                val data = buffer.copyOf(bytesRead)
                val isLast = bytesRead < bufferSize

                val packet =
                        TransferDataPacket(
                                transferId = transfer.id,
                                sequenceNumber = sequenceNumber,
                                data = data,
                                isLastPacket = isLast
                        )

                socketManager.sendData(packet.toBytes(), socket)

                totalSent += bytesRead
                sequenceNumber++

                // Update progress
                updateTransferProgress(transfer.id, totalSent, startTime)

                if (isLast) break
            }
        }

        // Send completion message
        val complete = TransferComplete(transferId = transfer.id, success = true)

        val completeJson = gson.toJson(complete)
        val message = ProtocolMessage(type = MessageType.TRANSFER_COMPLETE, payload = completeJson)

        val messageJson = gson.toJson(message)
        socketManager.sendData(messageJson.toByteArray(), socket)
    }

    // MARK: - Receive Files
    private fun handleReceivedData(data: ByteArray, connectionId: String) {
        scope.launch {
            try {
                // Try to parse as protocol message first
                val messageJson = String(data)
                val message = gson.fromJson(messageJson, ProtocolMessage::class.java)

                when (message.type) {
                    MessageType.TRANSFER_REQUEST -> handleTransferRequest(message, connectionId)
                    MessageType.TRANSFER_COMPLETE -> handleTransferComplete(message)
                    else -> {}
                }
            } catch (e: Exception) {
                // Try to parse as data packet
                try {
                    val packet = TransferDataPacket.fromBytes(data)
                    if (packet != null) {
                        handleDataPacket(packet)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse received data", e)
                }
            }
        }
    }

    private fun handleTransferRequest(message: ProtocolMessage, connectionId: String) {
        val request = gson.fromJson(message.payload, TransferRequest::class.java)

        // Auto-accept for now
        val response = TransferResponse(transferId = request.transferId, accepted = true)

        val responseJson = gson.toJson(response)
        val responseMessage =
                ProtocolMessage(type = MessageType.TRANSFER_RESPONSE, payload = responseJson)

        // Send response (simplified - need to get socket from connectionId)
        Log.d(TAG, "Transfer request received: ${request.fileName}")

        // Initialize transfer buffer
        transferBuffers[request.transferId] = ByteArray(0)
    }

    private fun handleDataPacket(packet: TransferDataPacket) {
        val currentBuffer = transferBuffers[packet.transferId] ?: ByteArray(0)
        transferBuffers[packet.transferId] = currentBuffer + packet.data

        if (packet.isLastPacket) {
            saveReceivedFile(packet.transferId)
        }
    }

    private fun saveReceivedFile(transferId: String) {
        val data = transferBuffers[transferId] ?: return

        try {
            val downloadsDir = context.getExternalFilesDir(null)
            val file = File(downloadsDir, "received_$transferId")

            FileOutputStream(file).use { output -> output.write(data) }

            Log.d(TAG, "File saved to: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save file", e)
        } finally {
            transferBuffers.remove(transferId)
        }
    }

    private fun handleTransferComplete(message: ProtocolMessage) {
        val complete = gson.fromJson(message.payload, TransferComplete::class.java)
        Log.d(TAG, "Transfer completed: ${complete.transferId}, success: ${complete.success}")
    }

    // MARK: - Transfer Management
    private fun addTransfer(transfer: TransferProgress) {
        val currentTransfers = _activeTransfers.value.toMutableList()
        currentTransfers.add(transfer)
        _activeTransfers.value = currentTransfers
    }

    private fun updateTransferProgress(
            transferId: String,
            bytesTransferred: Long,
            startTime: Long
    ) {
        val currentTransfers = _activeTransfers.value.toMutableList()
        val index = currentTransfers.indexOfFirst { it.id == transferId }

        if (index >= 0) {
            val transfer = currentTransfers[index]
            val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
            val speed = if (elapsed > 0) bytesTransferred / elapsed else 0.0

            currentTransfers[index] =
                    transfer.copy(bytesTransferred = bytesTransferred, speed = speed)
            _activeTransfers.value = currentTransfers
        }
    }

    private fun updateTransferStatus(transferId: String, status: TransferProgress.TransferStatus) {
        val currentTransfers = _activeTransfers.value.toMutableList()
        val index = currentTransfers.indexOfFirst { it.id == transferId }

        if (index >= 0) {
            currentTransfers[index] = currentTransfers[index].copy(status = status)
            _activeTransfers.value = currentTransfers
        }
    }

    private fun updateTransferError(transferId: String, error: String) {
        val currentTransfers = _activeTransfers.value.toMutableList()
        val index = currentTransfers.indexOfFirst { it.id == transferId }

        if (index >= 0) {
            currentTransfers[index] =
                    currentTransfers[index].copy(
                            status = TransferProgress.TransferStatus.FAILED,
                            error = error
                    )
            _activeTransfers.value = currentTransfers
        }
    }

    fun cancelTransfer(transferId: String) {
        updateTransferStatus(transferId, TransferProgress.TransferStatus.CANCELLED)
    }

    fun cleanup() {
        connections.values.forEach { it.close() }
        connections.clear()
        scope.cancel()
    }
}
