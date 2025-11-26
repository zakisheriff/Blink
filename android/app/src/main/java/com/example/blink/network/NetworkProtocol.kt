package com.example.blink.network

// MARK: - Network Protocol Constants
object NetworkConstants {
    const val DISCOVERY_PORT = 8888
    const val TRANSFER_PORT = 8889
    const val BROADCAST_INTERVAL = 2000L // milliseconds
    const val DEVICE_TIMEOUT = 10000L // milliseconds
    const val BUFFER_SIZE = 65536 // 64KB chunks
    const val PROTOCOL_VERSION = "1.0"
}

// MARK: - Message Types
enum class MessageType {
    DISCOVERY,
    DISCOVERY_RESPONSE,
    PAIR_REQUEST,
    PAIR_RESPONSE,
    TRANSFER_REQUEST,
    TRANSFER_RESPONSE,
    TRANSFER_DATA,
    TRANSFER_COMPLETE,
    TRANSFER_ERROR,
    PROGRESS,
    PING,
    PONG
}

// MARK: - Protocol Message
data class ProtocolMessage(
        val type: MessageType,
        val timestamp: Long = System.currentTimeMillis(),
        val payload: String? = null
)

// MARK: - Discovery Message
data class DiscoveryMessage(
        val deviceId: String,
        val deviceName: String,
        val deviceType: String = "android",
        val port: Int,
        val protocolVersion: String = NetworkConstants.PROTOCOL_VERSION,
        val code: String? = null,
        val type: String = "presence" // "presence", "query", "response"
)

// MARK: - Pair Request
data class PairRequest(
        val deviceId: String,
        val deviceName: String,
        val timestamp: Long = System.currentTimeMillis()
)

// MARK: - Pair Response
data class PairResponse(
        val accepted: Boolean,
        val deviceId: String,
        val deviceName: String,
        val sessionToken: String? = null
)

// MARK: - Transfer Request
data class TransferRequest(
        val transferId: String,
        val fileName: String,
        val fileSize: Long,
        val isDirectory: Boolean,
        val checksum: String? = null
)

// MARK: - Transfer Response
data class TransferResponse(
        val transferId: String,
        val accepted: Boolean,
        val reason: String? = null
)

// MARK: - Transfer Data Packet
data class TransferDataPacket(
        val transferId: String,
        val sequenceNumber: Long,
        val data: ByteArray,
        val isLastPacket: Boolean
) {
    fun toBytes(): ByteArray {
        val result = mutableListOf<Byte>()

        // Transfer ID (36 bytes for UUID string)
        val idBytes = transferId.toByteArray().take(36)
        result.addAll(idBytes)
        result.addAll(List(36 - idBytes.size) { 0.toByte() })

        // Sequence number (8 bytes)
        val seqBytes = ByteArray(8)
        for (i in 0..7) {
            seqBytes[7 - i] = (sequenceNumber shr (i * 8)).toByte()
        }
        result.addAll(seqBytes.toList())

        // Is last packet flag (1 byte)
        result.add(if (isLastPacket) 1 else 0)

        // Data length (4 bytes)
        val length = data.size
        val lengthBytes = ByteArray(4)
        for (i in 0..3) {
            lengthBytes[3 - i] = (length shr (i * 8)).toByte()
        }
        result.addAll(lengthBytes.toList())

        // Actual data
        result.addAll(data.toList())

        return result.toByteArray()
    }

    companion object {
        fun fromBytes(bytes: ByteArray): TransferDataPacket? {
            if (bytes.size < 49) return null // Minimum size

            var offset = 0

            // Transfer ID
            val transferId = String(bytes.sliceArray(offset until offset + 36)).trim('\u0000')
            offset += 36

            // Sequence number
            var sequenceNumber = 0L
            for (i in 0..7) {
                sequenceNumber = (sequenceNumber shl 8) or (bytes[offset + i].toLong() and 0xFF)
            }
            offset += 8

            // Is last packet
            val isLastPacket = bytes[offset] == 1.toByte()
            offset += 1

            // Data length
            var length = 0
            for (i in 0..3) {
                length = (length shl 8) or (bytes[offset + i].toInt() and 0xFF)
            }
            offset += 4

            // Actual data
            if (bytes.size < offset + length) return null
            val data = bytes.sliceArray(offset until offset + length)

            return TransferDataPacket(transferId, sequenceNumber, data, isLastPacket)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TransferDataPacket

        if (transferId != other.transferId) return false
        if (sequenceNumber != other.sequenceNumber) return false
        if (!data.contentEquals(other.data)) return false
        if (isLastPacket != other.isLastPacket) return false

        return true
    }

    override fun hashCode(): Int {
        var result = transferId.hashCode()
        result = 31 * result + sequenceNumber.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + isLastPacket.hashCode()
        return result
    }
}

// MARK: - Progress Update
data class ProgressUpdate(
        val transferId: String,
        val bytesTransferred: Long,
        val totalBytes: Long,
        val speed: Double
)

// MARK: - Transfer Complete
data class TransferComplete(
        val transferId: String,
        val success: Boolean,
        val checksum: String? = null,
        val error: String? = null
)
