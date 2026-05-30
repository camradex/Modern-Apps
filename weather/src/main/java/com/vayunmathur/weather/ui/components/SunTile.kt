package com.vayunmathur.weather.ui.components

import androidx.compose.foundation.layout.offset
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vayunmathur.weather.R
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SunBlock(sunriseEpochSec: Long?, sunsetEpochSec: Long?, modifier: Modifier = Modifier) {
    val sunrise = sunriseEpochSec?.let { formatTime(it) } ?: "—"
    val sunset = sunsetEpochSec?.let { formatTime(it) } ?: "—"
    WeatherBlock(
        title = "Sun",
        iconRes = R.drawable.outline_clear_day_24,
        modifier = modifier,
        shape = MaterialShapes.Cookie12Sided.toShape(),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            sunrise,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.align(Alignment.Center).offset(y = (-12).dp),
        )
        Text(
            "Sets $sunset",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
            modifier = Modifier.align(Alignment.BottomCenter).offset(y = (-20).dp),
        )
    }
}

private fun formatTime(epochSec: Long): String {
    val ldt = Instant.fromEpochSeconds(epochSec)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val h = ldt.hour
    val m = ldt.minute
    val display = if (h % 12 == 0) 12 else h % 12
    val ampm = if (h < 12) "AM" else "PM"
    val mm = m.toString().padStart(2, '0')
    return "$display:$mm $ampm"
}
