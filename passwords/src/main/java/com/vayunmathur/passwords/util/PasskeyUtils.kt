package com.vayunmathur.passwords.util

import android.content.Context
import android.content.Intent
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.credentials.provider.CallingAppInfo
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

object PasskeyUtils {

    private const val TAG = "PasskeyUtils"
    private const val EXTRA_TIMESTAMP = "com.vayunmathur.passwords.extra.timestamp"
    private const val EXTRA_AUTH_CODE = "com.vayunmathur.passwords.extra.authCode"
    private const val HMAC_KEY_ALIAS = "PasswordsPasskeyHMACKey"
    private const val KEYSTORE_TYPE = "AndroidKeyStore"
    private const val MAX_AGE_SECONDS = 60

    private val secureRandom = SecureRandom()

    fun generateCredentialId(): ByteArray {
        val id = ByteArray(16)
        secureRandom.nextBytes(id)
        return id
    }

    // -- HMAC PendingIntent security --

    fun Intent.addAuthCode(entryId: String? = null) {
        val timestamp = Instant.now().epochSecond
        putExtra(EXTRA_TIMESTAMP, timestamp.toString())
        putExtra(EXTRA_AUTH_CODE, generateHmac(entryId, timestamp).toHexString())
    }

    fun verifyAuthCode(intent: Intent, entryId: String? = null) {
        val timestampStr = intent.getStringExtra(EXTRA_TIMESTAMP)
            ?: throw SecurityException("Missing timestamp")
        val timestamp = timestampStr.toLongOrNull()
            ?: throw SecurityException("Invalid timestamp")
        val elapsed = Instant.now().epochSecond - timestamp
        if (elapsed !in 0..MAX_AGE_SECONDS) {
            throw SecurityException("PendingIntent expired")
        }
        val expected = generateHmac(entryId, timestamp)
        val actual = intent.getStringExtra(EXTRA_AUTH_CODE)
            ?.hexToByteArray()
            ?: throw SecurityException("Missing auth code")
        if (!MessageDigest.isEqual(expected, actual)) {
            throw SecurityException("Auth code mismatch")
        }
    }

    private fun generateHmac(entryId: String?, timestamp: Long): ByteArray {
        val message = "${entryId ?: "new"}:$timestamp"
        val key = getOrCreateHmacKey()
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(key)
        return mac.doFinal(message.toByteArray())
    }

    private fun getOrCreateHmacKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_TYPE)
        keyStore.load(null)
        return try {
            keyStore.getKey(HMAC_KEY_ALIAS, null) as SecretKey
        } catch (_: Exception) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_HMAC_SHA256, KEYSTORE_TYPE
            )
            keyGenerator.init(
                KeyGenParameterSpec.Builder(HMAC_KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
                    .setKeySize(256)
                    .build()
            )
            keyGenerator.generateKey()
        }
    }

    // -- Origin resolution --

    fun getPrivilegedOrigin(callingAppInfo: CallingAppInfo, context: Context): String? {
        return try {
            val allowList = context.assets.open("passkeys_privileged_browsers.json")
                .bufferedReader().readText()
            val origin = callingAppInfo.getOrigin(allowList)
            if (!origin.isNullOrEmpty()) {
                Log.d(TAG, "Resolved privileged browser origin: $origin")
                origin.removeSuffix("/")
            } else null
        } catch (e: Exception) {
            Log.d(TAG, "No privileged browser match: ${e.message}")
            null
        }
    }

    fun getAndroidOrigin(callingAppInfo: CallingAppInfo): String {
        val fingerprint = callingAppInfo.signingInfo
            .apkContentsSigners
            .firstOrNull()
            ?.toByteArray()
            ?.let { MessageDigest.getInstance("SHA-256").digest(it) }
            ?.toHexString()
            ?: "unknown"
        return "android:apk-key-hash:$fingerprint"
    }

    // -- AuthenticatorData builder --

    fun buildAuthenticatorData(
        rpId: String,
        userPresent: Boolean = true,
        userVerified: Boolean = true,
        backupEligible: Boolean = true,
        backupState: Boolean = true,
        attestedCredentialData: Boolean = false,
        signCount: Int = 0,
    ): ByteArray {
        var flags = 0
        if (userPresent) flags = flags or 0x01
        if (userVerified) flags = flags or 0x04
        if (backupEligible) flags = flags or 0x08
        if (backupState) flags = flags or 0x10
        if (attestedCredentialData) flags = flags or 0x40

        val rpIdHash = MessageDigest.getInstance("SHA-256").digest(rpId.toByteArray())
        return rpIdHash +
            byteArrayOf(flags.toByte()) +
            byteArrayOf(
                (signCount shr 24).toByte(),
                (signCount shr 16).toByte(),
                (signCount shr 8).toByte(),
                signCount.toByte()
            )
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02X".format(it) }

    private fun String.hexToByteArray(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
