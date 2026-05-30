package com.vayunmathur.library.intents.weather

import kotlinx.serialization.Serializable

/**
 * Cross-app weather payload returned by the Weather app's
 * `GetWeatherIntent` and `GetWeatherByNameIntent` activities to anything that
 * launches them via `IntentLauncher` — currently OpenAssistant's `get_weather`
 * / `get_weather_by_name` tools.
 *
 * All temperatures are in Celsius, all wind speeds in km/h. The receiving
 * caller (typically the LLM via `toString()`) is responsible for any unit
 * conversion shown to the user. Sunrise/sunset are epoch seconds in UTC, so
 * the caller renders them in whichever timezone is appropriate.
 *
 * [error] is non-null only when the lookup failed (e.g. city not found, no
 * network). The other numeric fields will be at their defaults in that case;
 * callers should check [error] first.
 */
@Serializable
data class WeatherData(
    val locationName: String?,
    val temperatureCelsius: Double,
    val feelsLikeCelsius: Double,
    val condition: String,
    val highCelsius: Double,
    val lowCelsius: Double,
    val precipitationChancePercent: Int,
    val humidityPercent: Int,
    val windKph: Double,
    val windDirection: String,
    val uvIndex: Double,
    val sunriseEpochSec: Long? = null,
    val sunsetEpochSec: Long? = null,
    val error: String? = null,
)
