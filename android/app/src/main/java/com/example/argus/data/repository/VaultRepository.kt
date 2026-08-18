package com.example.argus.data.repository

import com.example.argus.crypto.vault.ArgusVaultCipher
import com.example.argus.data.local.ArgusLocalStore
import com.example.argus.data.model.VaultItem
import com.example.argus.data.model.VaultItemType
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

class VaultRepository(
    private val localStore: ArgusLocalStore,
    private val storageDir: File
) {
    val vaultItems: StateFlow<List<VaultItem>> = localStore.vaultItemsFlow

    fun saveEncryptedNote(title: String, noteText: String): VaultItem {
        val id = UUID.randomUUID().toString()
        val encryptedBlob = ArgusVaultCipher.encryptBytes(noteText.toByteArray(Charsets.UTF_8))
        // Store ciphertext & IV bundle
        val encodedPayload = "${encryptedBlob.ivBase64}:${encryptedBlob.ciphertextBase64}"

        val item = VaultItem(
            id = id,
            title = title,
            type = VaultItemType.NOTE,
            contentOrPath = encodedPayload,
            fileSizeBytes = noteText.length.toLong(),
            mimeType = "text/plain",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isLocked = true
        )

        localStore.saveVaultItem(item)
        return item
    }

    fun decryptNote(item: VaultItem): String {
        val parts = item.contentOrPath.split(":")
        if (parts.size != 2) return item.contentOrPath
        val iv = parts[0]
        val ciphertext = parts[1]
        val decryptedBytes = ArgusVaultCipher.decryptBytes(iv, ciphertext)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    fun importAndEncryptFile(sourceFile: File, title: String, type: VaultItemType, mimeType: String): VaultItem {
        val id = UUID.randomUUID().toString()
        val destEncryptedFile = File(storageDir, "vault_$id.enc")

        FileInputStream(sourceFile).use { input ->
            FileOutputStream(destEncryptedFile).use { output ->
                ArgusVaultCipher.encryptStream(input, output)
            }
        }

        val item = VaultItem(
            id = id,
            title = title,
            type = type,
            contentOrPath = destEncryptedFile.absolutePath,
            fileSizeBytes = sourceFile.length(),
            mimeType = mimeType,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isLocked = true
        )

        localStore.saveVaultItem(item)
        return item
    }

    fun decryptFileToTemp(item: VaultItem, destTempFile: File) {
        val encFile = File(item.contentOrPath)
        FileInputStream(encFile).use { input ->
            FileOutputStream(destTempFile).use { output ->
                ArgusVaultCipher.decryptStream(input, output)
            }
        }
    }

    fun deleteItem(id: String) {
        val item = vaultItems.value.firstOrNull { it.id == id }
        if (item != null && item.type != VaultItemType.NOTE) {
            val f = File(item.contentOrPath)
            if (f.exists()) f.delete()
        }
        localStore.deleteVaultItem(id)
    }
}
