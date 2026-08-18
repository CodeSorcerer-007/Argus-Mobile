package com.example.argus.core.common

import java.util.Base64

object Base64Compat {
    fun encodeToString(bytes: ByteArray): String {
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun decode(base64String: String): ByteArray {
        // Strip any whitespace or line breaks
        val clean = base64String.trim().replace("\n", "").replace("\r", "")
        return Base64.getDecoder().decode(clean)
    }
}
