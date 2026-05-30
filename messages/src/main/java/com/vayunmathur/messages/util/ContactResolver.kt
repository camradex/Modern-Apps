package com.vayunmathur.messages.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Resolves a phone number to a device-contact display name + photo URI.
 *
 * Backed by [ContactsContract.PhoneLookup], which Android's contact
 * subsystem queries via the same E.164/national fuzzy match the system
 * Phone / Messages apps use. Returns null when the permission is missing
 * or no contact matches.
 *
 * Why we use this even though Google Messages already sends display
 * names: those names come from the user's Google contacts (which can
 * lag, conflict, or be empty for numbers not in Google contacts). The
 * device's contact database is canonical for the user's intent — we
 * always prefer it.
 */
object ContactResolver {

    private const val TAG = "ContactResolver"

    data class Result(
        val displayName: String?,
        /** content:// URI suitable for Coil/Glide loading. */
        val photoUri: String?,
    )

    fun lookup(context: Context, phoneNumber: String): Result? {
        if (phoneNumber.isBlank()) return null
        if (!hasPermission(context)) return null
        // PHONE_LOOKUP is the right URI for "I have a phone number and
        // want the contact" — handles per-region number normalization
        // internally so "+1 555-555-1234" and "5555551234" both match.
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber),
        )
        val projection = arrayOf(
            ContactsContract.PhoneLookup.DISPLAY_NAME,
            ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI,
            ContactsContract.PhoneLookup.PHOTO_URI,
        )
        return try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val name = cursor.getString(0)?.takeIf { it.isNotBlank() }
                // Prefer the full PHOTO_URI; fall back to thumbnail. Both
                // are content:// URIs that Coil can render.
                val photo = cursor.getString(2)?.takeIf { it.isNotBlank() }
                    ?: cursor.getString(1)?.takeIf { it.isNotBlank() }
                Result(displayName = name, photoUri = photo)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "PhoneLookup for $phoneNumber failed: ${t.message}")
            null
        }
    }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
}
