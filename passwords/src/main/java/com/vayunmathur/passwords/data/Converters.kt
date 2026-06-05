package com.vayunmathur.passwords.data

import android.util.Base64
import androidx.room.TypeConverter

object Converters {
    private const val DELIM = "|||"

    @TypeConverter
    @JvmStatic
    fun fromWebsites(list: List<String>?): String = (list ?: emptyList()).joinToString(DELIM) { it }

    @TypeConverter
    @JvmStatic
    fun toWebsites(value: String?): List<String> = value?.takeIf { it.isNotEmpty() }?.split(DELIM) ?: emptyList()
}
