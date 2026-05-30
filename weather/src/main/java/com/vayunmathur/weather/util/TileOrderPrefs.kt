package com.vayunmathur.weather.util

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.tileOrderDataStore by preferencesDataStore(name = "weather_tile_order")
private val KEY_ORDER = stringPreferencesKey("tile_order")

/** Stable identifiers for the customizable metric tiles. */
enum class WeatherTile { AirQuality, Sun, Visibility, Pressure, Humidity, Pollen, UV, Wind }

/**
 * User-customizable order of metric tiles in the home grid. Persisted as a
 * comma-separated string of enum names so future tiles can be added without
 * a migration — unknown / new tiles are appended at end on each read.
 */
class TileOrderPrefs(private val context: Context) {

    val order: Flow<List<WeatherTile>> = context.tileOrderDataStore.data.map { prefs ->
        val saved = prefs[KEY_ORDER]
            ?.split(',')
            ?.mapNotNull { name -> runCatching { WeatherTile.valueOf(name) }.getOrNull() }
            ?: emptyList()
        // Append any tile not yet in the saved list (new tiles introduced
        // after the user customized).
        val remaining = WeatherTile.entries.filter { it !in saved }
        saved + remaining
    }

    suspend fun setOrder(order: List<WeatherTile>) {
        context.tileOrderDataStore.edit { prefs ->
            prefs[KEY_ORDER] = order.joinToString(",") { it.name }
        }
    }
}
