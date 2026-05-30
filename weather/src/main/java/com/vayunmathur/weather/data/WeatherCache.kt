package com.vayunmathur.weather.data

import androidx.room.Entity

/**
 * Most-recent forecast JSON for a (lat, lon) point. Lets the home pager
 * render instantly while a refresh runs in the background, and lets the
 * OpenAssistant intent return the cached value when the network is down.
 *
 * Coordinates are rounded to 4 decimals (≈11 m) before insertion so we don't
 * cache 1000 near-identical rows for tiny GPS jitter.
 */
@Entity(primaryKeys = ["latRounded", "lonRounded"])
data class WeatherCache(
    val latRounded: Double,
    val lonRounded: Double,
    val forecastJson: String,
    val fetchedAtEpochMs: Long,
)
