package com.vayunmathur.weather.util

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.unitsDataStore by preferencesDataStore(name = "weather_units")
private val KEY_FAHRENHEIT = booleanPreferencesKey("use_fahrenheit")
private val KEY_MPH = booleanPreferencesKey("use_mph")

/**
 * User-selected display units. Defaults follow Pixel Weather: °F + mph in the
 * US, °C + km/h elsewhere — approximated here as "default to metric" since we
 * don't want to introduce locale-detection logic on the hot path.
 */
class UnitsPrefs(private val context: Context) {

    val tempUnit: Flow<TemperatureUnit> = context.unitsDataStore.data.map {
        if (it[KEY_FAHRENHEIT] == true) TemperatureUnit.Fahrenheit else TemperatureUnit.Celsius
    }

    val windUnit: Flow<WindUnit> = context.unitsDataStore.data.map {
        if (it[KEY_MPH] == true) WindUnit.Mph else WindUnit.KmH
    }

    suspend fun setTempUnit(unit: TemperatureUnit) {
        context.unitsDataStore.edit { it[KEY_FAHRENHEIT] = (unit == TemperatureUnit.Fahrenheit) }
    }

    suspend fun setWindUnit(unit: WindUnit) {
        context.unitsDataStore.edit { it[KEY_MPH] = (unit == WindUnit.Mph) }
    }
}
