package com.vayunmathur.weather.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vayunmathur.weather.network.Hourly
import com.vayunmathur.weather.util.TemperatureUnit
import com.vayunmathur.weather.util.formatTemperatureCompact
import com.vayunmathur.weather.util.weatherConditionForCode
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * 24-hour preview rendered inside a [WeatherSection] card. First cell shows
 * "Now" and is filled with `surfaceContainerHighest` so the user's gaze
 * lands there first (mirrors Pixel Weather's "now" pill).
 */
@Composable
fun HourlyStrip(hourly: Hourly, tempUnit: TemperatureUnit) {
    val now = System.currentTimeMillis() / 1000
    val cells = remember(hourly) {
        hourly.time.indices
            .mapNotNull { i ->
                val ts = parseIsoToEpochSec(hourly.time.getOrNull(i)) ?: return@mapNotNull null
                if (ts < now - 3600) return@mapNotNull null
                HourlyCell(
                    epochSec = ts,
                    temperature = hourly.temperature.getOrNull(i) ?: 0.0,
                    weatherCode = hourly.weatherCode.getOrNull(i) ?: 0,
                    precip = hourly.precipitationProbability.getOrNull(i) ?: 0,
                    isDay = (hourly.isDay.getOrNull(i) ?: 1) == 1,
                )
            }
            .take(24)
    }
    if (cells.isEmpty()) return

    WeatherSection(
        title = "Hourly forecast",
        leadingIconRes = com.vayunmathur.weather.R.drawable.outline_schedule_24,
        surfacePadding = PaddingValues(vertical = 8.dp),
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            itemsIndexed(cells) { index, cell ->
                HourlyCellView(cell, tempUnit, highlightAsNow = index == 0)
            }
        }
    }
}

@Composable
private fun HourlyCellView(cell: HourlyCell, tempUnit: TemperatureUnit, highlightAsNow: Boolean) {
    val condition = weatherConditionForCode(cell.weatherCode)
    val label = if (highlightAsNow) "Now" else formatHour(cell.epochSec)
    val timeColor = if (highlightAsNow) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
    Column(
        modifier = Modifier
            .padding(horizontal = 4.dp, vertical = 8.dp)
            .width(60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Pixel order: temp on top, condition icon, precip %, then time.
        Text(
            formatTemperatureCompact(cell.temperature, tempUnit),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )
        Icon(
            painter = painterResource(condition.iconRes(cell.isDay)),
            contentDescription = condition.label,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = if (cell.precip >= 10) "${cell.precip}%" else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = timeColor,
            fontWeight = if (highlightAsNow) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

private data class HourlyCell(
    val epochSec: Long,
    val temperature: Double,
    val weatherCode: Int,
    val precip: Int,
    val isDay: Boolean,
)

private fun parseIsoToEpochSec(iso: String?): Long? {
    if (iso == null) return null
    return runCatching { Instant.parse("$iso:00Z").epochSeconds }.getOrNull()
        ?: runCatching { Instant.parse(iso).epochSeconds }.getOrNull()
}

private fun formatHour(epochSec: Long): String {
    val ldt = Instant.fromEpochSeconds(epochSec)
        .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
    val h = ldt.hour
    val ampm = if (h < 12) "AM" else "PM"
    val display = if (h % 12 == 0) 12 else h % 12
    return "$display $ampm"
}

private inline fun <T> androidx.compose.foundation.lazy.LazyListScope.itemsIndexed(
    items: List<T>,
    crossinline content: @Composable (Int, T) -> Unit,
) {
    items(items.size) { content(it, items[it]) }
}
