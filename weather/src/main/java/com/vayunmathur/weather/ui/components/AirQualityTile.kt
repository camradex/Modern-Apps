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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AirQualityBlock(aqi: Int?, modifier: Modifier = Modifier) {
    val v = aqi ?: 0
    val label = when {
        aqi == null -> "—"
        v <= 50 -> "Good"
        v <= 100 -> "Moderate"
        v <= 150 -> "Sensitive"
        v <= 200 -> "Unhealthy"
        v <= 300 -> "Very unhealthy"
        else -> "Hazardous"
    }
    WeatherBlock(
        title = "Air quality",
        iconRes = R.drawable.outline_air_24,
        modifier = modifier,
        shape = MaterialShapes.Cookie12Sided.toShape(),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            aqi?.toString() ?: "—",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.align(Alignment.Center).offset(y = 4.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
            modifier = Modifier.align(Alignment.BottomCenter).offset(y = (-20).dp),
        )
    }
}
