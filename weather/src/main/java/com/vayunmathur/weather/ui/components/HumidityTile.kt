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
import com.vayunmathur.weather.util.TemperatureUnit
import com.vayunmathur.weather.util.formatTemperatureCompact

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HumidityBlock(
    relativeHumidity: Int,
    dewPointCelsius: Double,
    tempUnit: TemperatureUnit,
    modifier: Modifier = Modifier,
) {
    WeatherBlock(
        title = "Humidity",
        iconRes = R.drawable.outline_drizzle_24,
        modifier = modifier,
        shape = MaterialShapes.Cookie12Sided.toShape(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(
            "$relativeHumidity%",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.Center).offset(y = 4.dp),
        )
        Text(
            "Dew point ${formatTemperatureCompact(dewPointCelsius, tempUnit)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.BottomCenter).offset(y = (-20).dp),
        )
    }
}
