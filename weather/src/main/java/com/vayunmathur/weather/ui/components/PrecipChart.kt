package com.vayunmathur.weather.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.vayunmathur.weather.network.Hourly
import kotlin.time.Instant

/**
 * 24-hour precipitation-probability bars inside a [WeatherSection]. Bars get
 * more opaque as the chance climbs so a quick glance shows the rainy
 * stretch.
 */
@Composable
fun PrecipChart(hourly: Hourly) {
    if (hourly.precipitationProbability.isEmpty()) return
    val nowSec = System.currentTimeMillis() / 1000
    val pairs: List<Int> = hourly.time
        .withIndex()
        .mapNotNull { (i, t) ->
            val ts = runCatching { Instant.parse("$t:00Z").epochSeconds }.getOrNull()
                ?: runCatching { Instant.parse(t).epochSeconds }.getOrNull()
                ?: return@mapNotNull null
            if (ts < nowSec - 3600) null else hourly.precipitationProbability.getOrNull(i)
        }
        .filterNotNull()
        .take(24)
    if (pairs.isEmpty() || pairs.max() == 0) return

    val barColor = MaterialTheme.colorScheme.onSurface
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest

    WeatherSection(title = "Precipitation", surfacePadding = PaddingValues(16.dp)) {
        Text(
            "Max ${pairs.max()}% in the next 24 hours",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(top = 12.dp),
        ) {
            val w = size.width
            val h = size.height
            val n = pairs.size
            val barW = w / n
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(0f, h - 2.dp.toPx()),
                size = Size(w, 2.dp.toPx()),
                cornerRadius = CornerRadius(1.dp.toPx()),
            )
            pairs.forEachIndexed { i, p ->
                val frac = p / 100f
                val barH = h * frac
                val alpha = (0.30f + 0.65f * frac).coerceAtMost(1f)
                drawRoundRect(
                    color = barColor.copy(alpha = alpha),
                    topLeft = Offset(i * barW + barW * 0.20f, h - barH),
                    size = Size(barW * 0.6f, barH),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("NOW", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("12H", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("24H", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
