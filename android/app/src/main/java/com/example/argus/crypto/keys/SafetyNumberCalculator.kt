package com.example.argus.crypto.keys

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.security.MessageDigest

object SafetyNumberCalculator {

    /**
     * Compute a 60-digit safety number fingerprint from two parties' identity public keys.
     * The calculation sorts the keys lexicographically so both parties compute the exact same number.
     */
    fun computeSafetyNumber(
        userId1: String,
        identityKeyBase64_1: String,
        userId2: String,
        identityKeyBase64_2: String
    ): String {
        val (firstUser, secondUser) = if (userId1 < userId2) {
            Pair(userId1 to identityKeyBase64_1, userId2 to identityKeyBase64_2)
        } else {
            Pair(userId2 to identityKeyBase64_2, userId1 to identityKeyBase64_1)
        }

        val digest = MessageDigest.getInstance("SHA-512")
        digest.update("ArgusSafetyNumber_v1:".toByteArray(Charsets.UTF_8))
        digest.update(firstUser.first.toByteArray(Charsets.UTF_8))
        digest.update(firstUser.second.toByteArray(Charsets.UTF_8))
        digest.update(secondUser.first.toByteArray(Charsets.UTF_8))
        digest.update(secondUser.second.toByteArray(Charsets.UTF_8))

        var hash = digest.digest()
        // Iterated hashing (5200 rounds) matching modern Signal fingerprint specification
        for (i in 0 until 5200) {
            val md = MessageDigest.getInstance("SHA-512")
            md.update(hash)
            md.update(firstUser.second.toByteArray(Charsets.UTF_8))
            hash = md.digest()
        }

        val digits = StringBuilder()
        for (chunk in 0 until 12) {
            val offset = chunk * 4
            val num = ((hash[offset].toInt() and 0xFF) shl 24) or
                    ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                    ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                    (hash[offset + 3].toInt() and 0xFF)
            val positiveNum = (num.toLong() and 0xFFFFFFFFL) % 100000
            digits.append(String.format("%05d", positiveNum))
        }

        // Format as 12 groups of 5 digits: XXXXX XXXXX ...
        return digits.toString().chunked(5).joinToString(" ")
    }

    /**
     * Generate a QR Bitmap for in-person visual security code verification
     */
    fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)

        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(
                    x,
                    y,
                    if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                )
            }
        }
        return bitmap
    }
}
