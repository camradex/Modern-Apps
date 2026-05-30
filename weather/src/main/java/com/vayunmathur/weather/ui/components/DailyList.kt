package com.vayunmathur.weather.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vayunmathur.weather.network.Daily
import com.vayunmathur.weather.util.TemperatureUnit
import com.vayunmathur.weather.util.formatTemperatureCompact
import com.vayunmathur.weather.util.weatherConditionForCode
import kotlinx.datetime.LocalDate

/**
 * 7-day forecast list inside a [WeatherSection]. Each row mirrors Health's
 * `HealthRow` rhythm: 36dp circular tinted icon badge, label column, range
 * bar in the middle, hi/lo on the right. Inset hairline dividers between
 * rows.
 */
@Composable
fun DailyList(daily: Daily, tempUnit: TemperatureUnit) {
    if (daily.time.isEmpty()) return

    val weekLow = daily.temperatureMin.minOrNull() ?: 0.0
    val weekHigh = daily.temperatureMax.maxOrNull() ?: 0.0
    val span = (weekHigh - weekLow).takeIf { it > 0 } ?: 1.0

    WeatherSection(
        title = "7-day forecast",
        leadingIconRes = com.vayunmathur.weather.R.drawable.outline_calendar_24,
        surfacePadding = PaddingValues(vertical = 4.dp),
    ) {
        daily.time.forEachIndexed { i, dateStr ->
            val code = daily.weatherCode.getOrNull(i) ?: 0
            val hi = daily.temperatureMax.getOrNull(i) ?: 0.0
            val lo = daily.temperatureMin.getOrNull(i) ?: 0.0
            DailyRow(
                label = if (i == 0) "Today" else dayLabel(dateStr),
                weatherCode = code,
                lo = lo,
                hi = hi,
                weekLow = weekLow,
                span = span,
                tempUnit = tempUnit,
            )
            if (i < daily.time.size - 1) WeatherSectionDivider(insetStart = 60.dp)
        }
    }
}

@Composable
private fun DailyRow(
    label: String,
    weatherCode: Int,
    lo: Double,
    hi: Double,
    weekLow: Double,
    span: Double,
    tempUnit: TemperatureUnit,
) {
    val condition = weatherConditionForCode(weatherCode)
    val accent = MaterialTheme.colorScheme.onSurface
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val barColor = MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(condition.iconRes(true)),
                contentDescription = condition.label,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            modifier = Modifier.width(56.dp),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        Text(
            formatTemperatureCompact(lo, tempUnit),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(36.dp),
        )
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .padding(horizontal = 4.dp),
        ) {
            val w = size.width
            val h = size.height
            drawRoundRect(
                color = trackColor,
                size = Size(w, h),
                cornerRadius = CornerRadius(h / 2),
            )
            val startFrac = ((lo - weekLow) / span).toFloat().coerceIn(0f, 1f)
            val endFrac = ((hi - weekLow) / span).toFloat().coerceIn(0f, 1f)
            val left = w * startFrac
            val widthF = (w * (endFrac - startFrac)).coerceAtLeast(h)
            drawRoundRect(
                color = barColor,
                topLeft = Offset(left, 0f),
                size = Size(widthF, h),
                cornerRadius = CornerRadius(h / 2),
            )
        }
        Text(
            formatTemperatureCompact(hi, tempUnit),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(36.dp),
        )
    }
}

private fun dayLabel(dateStr: String): String {
    val date = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: return dateStr
    return date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
}
