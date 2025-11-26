package com.example.blink.data

import java.util.UUID

// MARK: - Device Model
data class Device(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val ipAddress: String,
    val port: Int,
    val deviceType: DeviceType = DeviceType.MAC,
    var lastSeen: Long = System.currentTimeMillis(),
    var isConnected: Boolean = false
) {
    enum class DeviceType {
        ANDROID, MAC, UNKNOWN
    }
}

// MARK: - Transfer Item
data class TransferItem(
    val id: String = UUID.randomUUID().toString(),
    val uri: String,
    val name: String,
    val size: Long,
    val isDirectory: Boolean = false
)

// MARK: - Transfer Progress
data class TransferProgress(
    val id: String = UUID.randomUUID().toString(),
    val item: TransferItem,
    val device: Device,
    var bytesTransferred: Long = 0,
    val totalBytes: Long = item.size,
    var speed: Double = 0.0, // bytes per second
    var status: TransferStatus = TransferStatus.PENDING,
    var error: String? = null
) {
    val progress: Float
        get() = if (totalBytes > 0) bytesTransferred.toFloat() / totalBytes.toFloat() else 0f
    
    val eta: Long?
        get() = if (speed > 0 && bytesTransferred < totalBytes) {
            val remaining = (totalBytes - bytesTransferred).toDouble()
            (remaining / speed).toLong()
        } else null
    
    enum class TransferStatus {
        PENDING, TRANSFERRING, COMPLETED, FAILED, CANCELLED
    }
}

// MARK: - Transfer Session
data class TransferSession(
    val id: String = UUID.randomUUID().toString(),
    val device: Device,
    val transfers: MutableList<TransferProgress> = mutableListOf()
) {
    val totalProgress: Float
        get() = if (transfers.isEmpty()) 0f
        else transfers.sumOf { it.progress.toDouble() }.toFloat() / transfers.size
}

// MARK: - QR Code Payload
data class QRCodePayload(
    val deviceId: String,
    val deviceName: String,
    val ipAddress: String,
    val port: Int,
    val timestamp: Long = System.currentTimeMillis()
)
