package com.vayunmathur.messages.signal.contacts

import android.util.Log
import com.vayunmathur.messages.signal.store.SignalRecipientStore
import com.vayunmathur.messages.signal.web.SignalWebSocket
import org.json.JSONObject
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class ProfileManager(
    private val ws: SignalWebSocket,
    private val recipientStore: SignalRecipientStore,
) {
    data class Profile(val name: String?, val about: String?, val avatarPath: String?)

    suspend fun fetchProfile(aci: String, profileKey: ByteArray?): Profile? {
        return try {
            val response = ws.sendRequest("GET", "/v1/profile/$aci",
                headers = mapOf("Accept-Language" to "en-US"))
            if (response.status !in 200..299) return null

            val json = JSONObject(String(response.body.toByteArray()))
            val encryptedName = json.optString("name", null)
            val encryptedAbout = json.optString("about", null)
            val avatarPath = json.optString("avatar", null)

            val name = if (encryptedName != null && profileKey != null) {
                decryptProfileField(android.util.Base64.decode(encryptedName, android.util.Base64.DEFAULT), profileKey)
            } else {
                null
            }

            val about = if (encryptedAbout != null && profileKey != null) {
                decryptProfileField(android.util.Base64.decode(encryptedAbout, android.util.Base64.DEFAULT), profileKey)
            } else {
                null
            }

            val profile = Profile(name = name, about = about, avatarPath = avatarPath)

            if (name != null) {
                val existing = recipientStore.getRecipient(aci)
                if (existing != null) {
                    recipientStore.storeRecipient(existing.copy(profileName = name, profileAvatar = avatarPath))
                }
            }

            profile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch profile for $aci", e)
            null
        }
    }

    private fun decryptProfileField(encryptedData: ByteArray, profileKey: ByteArray): String? {
        return try {
            if (encryptedData.size < 29) return null // NONCE(12) + TAG(16) + at least 1 byte
            val nonce = encryptedData.copyOfRange(0, 12)
            val ciphertext = encryptedData.copyOfRange(12, encryptedData.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(profileKey, "AES")
            val gcmSpec = GCMParameterSpec(128, nonce)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
            val decrypted = cipher.doFinal(ciphertext)
            String(decrypted).trimEnd('\u0000').replace('\u0000', ' ')
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt profile name", e)
            null
        }
    }

    companion object {
        private const val TAG = "ProfileManager"
    }
}
