package com.localdev.webdavserver.presentation.theme

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Human-readable byte formatting (KB / MB / GB).
 */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var idx = -1
    do {
        value /= 1024.0
        idx++
    } while (value >= 1024 && idx < units.size - 1)
    return "%.1f %s".format(value, units[idx])
}

/**
 * Format an uptime duration in HH:MM:SS.
 */
fun formatUptime(millis: Long): String {
    val totalSec = (millis / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
fun formatTime(millis: Long): String = timeFmt.format(Date(millis))

/**
 * Generates a QR code [ImageBitmap] for the given text.
 */
fun generateQrBitmap(text: String, size: Int = 512): ImageBitmap? {
    return runCatching {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1
        )
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        bmp.asImageBitmap()
    }.getOrNull()
}
