package com.vayunmathur.weather.intents

import com.vayunmathur.library.intents.weather.WeatherData
import com.vayunmathur.library.util.AssistantIntent
import com.vayunmathur.weather.network.WeatherApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

/** Input payload for [GetWeatherByNameIntent]: a free-form city/place name. */
@Serializable
data class LocationQueryInput(val name: String)

/**
 * "Headless" activity launched by OpenAssistant's `get_weather_by_name`
 * tool. Resolves [LocationQueryInput.name] via Open-Meteo's geocoding API,
 * then fetches a forecast for the first match. Returns [WeatherData] with
 * `error` set if no place matched or the network call failed.
 */
@OptIn(InternalSerializationApi::class)
class GetWeatherByNameIntent : AssistantIntent<LocationQueryInput, WeatherData>(
    inputSerializer = serializer<LocationQueryInput>(),
    outputSerializer = serializer<WeatherData>(),
) {
    override suspend fun performCalculation(input: LocationQueryInput): WeatherData {
        return try {
            val matches = WeatherApi.geocode(input.name, limit = 1).results
            val place = matches.firstOrNull() ?: return WeatherData(
                locationName = input.name,
                temperatureCelsius = 0.0,
                feelsLikeCelsius = 0.0,
                condition = "",
                highCelsius = 0.0,
                lowCelsius = 0.0,
                precipitationChancePercent = 0,
                humidityPercent = 0,
                windKph = 0.0,
                windDirection = "",
                uvIndex = 0.0,
                error = "No location matched '${input.name}'",
            )
            val forecast = WeatherApi.forecast(place.latitude, place.longitude)
            val label = listOfNotNull(place.name, place.country)
                .filter { it.isNotBlank() }
                .joinToString(", ")
            forecast.toWeatherData(locationName = label.ifBlank { place.name })
        } catch (e: Exception) {
            WeatherData(
                locationName = input.name,
                temperatureCelsius = 0.0,
                feelsLikeCelsius = 0.0,
                condition = "",
                highCelsius = 0.0,
                lowCelsius = 0.0,
                precipitationChancePercent = 0,
                humidityPercent = 0,
                windKph = 0.0,
                windDirection = "",
                uvIndex = 0.0,
                error = e.message ?: "Failed to fetch forecast",
            )
        }
    }
}
