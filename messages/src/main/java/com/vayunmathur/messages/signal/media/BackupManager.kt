package com.vayunmathur.messages.signal.media

import android.util.Log
import com.vayunmathur.messages.signal.web.SignalHttpClient
import com.vayunmathur.messages.signal.web.SignalWebSocket
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class BackupManager(
    private val ws: SignalWebSocket,
    private val selfAci: String,
) {
    companion object {
        private const val TAG = "BackupManager"
        private const val BACKUP_VERSION = 1
    }

    data class TransferArchiveMetadata(
        val cdn: Int,
        val key: String,
        val error: String?,
    )

    suspend fun waitForTransfer(
        ephemeralBackupKey: ByteArray?,
    ): TransferArchiveMetadata? {
        if (ephemeralBackupKey == null) {
            Log.w(TAG, "No ephemeral backup key")
            return null
        }
        return try {
            val resp = ws.sendRequest(
                "GET",
                "/v1/devices/transfer_archive?timeout=300",
                null,
            )
            when (resp.status) {
                204 -> null
                200 -> {
                    val json = JSONObject(resp.body.toStringUtf8())
                    TransferArchiveMetadata(
                        cdn = json.optInt("cdn", 0),
                        key = json.optString("key", ""),
                        error = json.optString("error", null),
                    )
                }
                else -> {
                    Log.w(TAG, "Transfer archive request failed: ${resp.status}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to wait for transfer", e)
            null
        }
    }

    suspend fun fetchAndProcessTransfer(
        meta: TransferArchiveMetadata,
        ephemeralBackupKey: ByteArray,
    ): Boolean {
        if (meta.error != null) {
            Log.e(TAG, "Transfer archive error: ${meta.error}")
            return false
        }
        return try {
            val keys = deriveTransferKeys(ephemeralBackupKey)
            val encrypted = downloadTransferArchive(meta) ?: return false
            if (!verifyMAC(keys.second, encrypted)) {
                Log.e(TAG, "Transfer archive MAC verification failed")
                return false
            }
            val decrypted = decryptTransferArchive(keys.first, encrypted)
            val decompressed = decompressGzip(decrypted)
            processBackupFrames(decompressed)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process transfer archive", e)
            false
        }
    }

    private suspend fun downloadTransferArchive(
        meta: TransferArchiveMetadata,
    ): ByteArray? {
        val host = SignalHttpClient.cdnHost(meta.cdn)
        val resp = SignalHttpClient.request(
            host = host,
            method = "GET",
            path = "/attachments/${meta.key}",
        )
        if (!resp.isSuccessful) return null
        return resp.body?.bytes()
    }

    private fun deriveTransferKeys(
        ephemeralBackupKey: ByteArray,
    ): Pair<ByteArray, ByteArray> {
        val expanded = StickerManager.hkdfExpand(
            ephemeralBackupKey, ByteArray(32),
            "Transfer Archive".toByteArray(), 64,
        )
        return Pair(expanded.copyOfRange(0, 32), expanded.copyOfRange(32, 64))
    }

    private fun verifyMAC(hmacKey: ByteArray, data: ByteArray): Boolean {
        if (data.size < 32) return false
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hmacKey, "HmacSHA256"))
        mac.update(data, 0, data.size - 32)
        val computed = mac.doFinal()
        val expected = data.copyOfRange(data.size - 32, data.size)
        return MessageDigest.isEqual(computed, expected)
    }

    private fun decryptTransferArchive(
        aesKey: ByteArray,
        data: ByteArray,
    ): ByteArray {
        val iv = data.copyOfRange(0, 16)
        val ciphertext = data.copyOfRange(16, data.size - 32)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(ciphertext)
    }

    private fun decompressGzip(data: ByteArray): ByteArray {
        val bais = ByteArrayInputStream(data)
        val gzip = GZIPInputStream(bais)
        val baos = ByteArrayOutputStream()
        gzip.copyTo(baos)
        gzip.close()
        return baos.toByteArray()
    }

    private fun processBackupFrames(data: ByteArray) {
        Log.d(TAG, "Processing ${data.size} bytes of backup data")
    }
}
