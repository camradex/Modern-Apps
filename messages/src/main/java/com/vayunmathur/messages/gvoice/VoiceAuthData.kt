package com.vayunmathur.messages.gvoice

import android.content.Context
import com.vayunmathur.library.util.DataStoreUtils
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Persisted authentication state for a Google Voice session.
 *
 * Voice has no QR-pair or OAuth-token API surface, so this is just a
 * map of the Google session cookies the user pasted at setup time.
 * Persisted to DataStore as a JSON blob under a dedicated key so the
 * gmessages auth state and this don't tread on each other.
 */
@Serializable
data class VoiceAuthData(
    val cookies: Map<String, String>,
) {
    fun hasRequired(): Boolean =
        VoiceEndpoints.RequiredCookies.all { cookies[it]?.isNotBlank() == true }

    suspend fun save(context: Context) {
        DataStoreUtils.getInstance(context).setString(
            DATA_STORE_KEY,
            Json.encodeToString(serializer(), this),
        )
    }

    companion object {
        private const val DATA_STORE_KEY = "gvoice_auth_data"

        suspend fun load(context: Context): VoiceAuthData? {
            val json = DataStoreUtils.getInstance(context).getString(DATA_STORE_KEY) ?: return null
            if (json.isBlank()) return null
            return runCatching { Json.decodeFromString(serializer(), json) }.getOrNull()
        }

        suspend fun clear(context: Context) {
            DataStoreUtils.getInstance(context).setString(DATA_STORE_KEY, "")
        }
    }
}
