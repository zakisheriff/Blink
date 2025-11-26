package com.example.blink.utils

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

// MARK: - File Size Formatting
fun Long.formatFileSize(): String {
    val bytes = this.toDouble()

    return when {
        bytes < 1024 -> "$this B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024)
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024 * 1024))
        else -> String.format("%.2f GB", bytes / (1024 * 1024 * 1024))
    }
}

// MARK: - Speed Formatting
fun Double.formatSpeed(): String {
    return when {
        this < 1024 -> String.format("%.0f B/s", this)
        this < 1024 * 1024 -> String.format("%.1f KB/s", this / 1024)
        this < 1024 * 1024 * 1024 -> String.format("%.1f MB/s", this / (1024 * 1024))
        else -> String.format("%.2f GB/s", this / (1024 * 1024 * 1024))
    }
}

// MARK: - Time Formatting
fun Long.formatETA(): String {
    val seconds = this

    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> {
            val minutes = seconds / 60
            val secs = seconds % 60
            "${minutes}m ${secs}s"
        }
        else -> {
            val hours = seconds / 3600
            val minutes = (seconds % 3600) / 60
            "${hours}h ${minutes}m"
        }
    }
}

// MARK: - QR Code Generation
fun generateQRCode(content: String, size: Int = 512): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }

        bitmap
    } catch (e: Exception) {
        null
    }
}
