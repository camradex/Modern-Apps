package com.vayunmathur.weather.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Instant

/**
 * Sun arc card — dashed full arc track, solid arc + dot up to the current
 * moment, sunrise/sunset labels underneath. Sits inside a [WeatherSection].
 */
@Composable
fun SunriseSunsetCard(sunriseEpochSec: Long?, sunsetEpochSec: Long?) {
    if (sunriseEpochSec == null || sunsetEpochSec == null || sunsetEpochSec <= sunriseEpochSec) return
    val nowSec = System.currentTimeMillis() / 1000
    val frac = ((nowSec - sunriseEpochSec).toDouble() / (sunsetEpochSec - sunriseEpochSec))
        .coerceIn(0.0, 1.0)
    val isDay = nowSec in sunriseEpochSec..sunsetEpochSec

    val arcColor = MaterialTheme.colorScheme.onSurface
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val sunColor = MaterialTheme.colorScheme.onSurface

    WeatherSection(title = "Sun", surfacePadding = PaddingValues(16.dp)) {
        Box(modifier = Modifier.fillMaxWidth().height(110.dp)) {
            Canvas(modifier = Modifier.fillMaxWidth().height(110.dp)) {
                val w = size.width
                val h = size.height
                val trackPath = Path().apply {
                    moveTo(0f, h)
                    for (deg in 180..360) {
                        val theta = Math.toRadians(deg.toDouble())
                        val x = (w / 2 + (w / 2) * cos(theta)).toFloat()
                        val y = (h + h * sin(theta)).toFloat()
                        lineTo(x, y)
                    }
                }
                drawPath(
                    trackPath,
                    color = trackColor,
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
                    ),
                )
                val activePath = Path().apply {
                    moveTo(0f, h)
                    val endDeg = 180 + 180 * frac
                    var d = 180.0
                    while (d <= endDeg) {
                        val theta = Math.toRadians(d)
                        val x = (w / 2 + (w / 2) * cos(theta)).toFloat()
                        val y = (h + h * sin(theta)).toFloat()
                        lineTo(x, y)
                        d += 1.0
                    }
                }
                drawPath(
                    activePath,
                    color = if (isDay) arcColor else trackColor,
                    style = Stroke(width = 2.5.dp.toPx()),
                )
                val theta = Math.toRadians(180 + 180 * frac)
                val sunX = (w / 2 + (w / 2) * cos(theta)).toFloat()
                val sunY = (h + h * sin(theta)).toFloat()
                drawCircle(
                    color = if (isDay) sunColor else trackColor,
                    radius = 7.dp.toPx(),
                    center = Offset(sunX, sunY),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "SUNRISE",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    formatTime(sunriseEpochSec),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    "SUNSET",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    formatTime(sunsetEpochSec),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
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
