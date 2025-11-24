package com.asimut.core.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

object BarcodeUtil {
    fun generateCode(message: String, format: String, size: Int = 600): Bitmap {
        val barcodeFormat = when (format) {
            "PKBarcodeFormatAztec", "AZTEC" -> BarcodeFormat.AZTEC
            "PKBarcodeFormatQR", "QR" -> BarcodeFormat.QR_CODE
            else -> BarcodeFormat.AZTEC
        }

        val bitMatrix: BitMatrix = MultiFormatWriter().encode(
            message,
            barcodeFormat,
            size,
            size
        )

        val bitmap = Bitmap.createBitmap(bitMatrix.width, bitMatrix.height, Bitmap.Config.RGB_565)
        for (x in 0 until bitMatrix.width) {
            for (y in 0 until bitMatrix.height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }
}
