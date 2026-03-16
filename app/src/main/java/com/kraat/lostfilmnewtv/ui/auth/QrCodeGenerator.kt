package com.kraat.lostfilmnewtv.ui.auth

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

object QrCodeGenerator {
    fun generateMatrix(content: String, size: Int) = QRCodeWriter().encode(
        content.trim().also {
            require(it.isNotEmpty()) { "QR content must not be blank." }
        },
        BarcodeFormat.QR_CODE,
        size,
        size,
    )

    fun generateImageBitmap(content: String, size: Int): ImageBitmap {
        val matrix = generateMatrix(content, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        return bitmap.asImageBitmap()
    }
}
