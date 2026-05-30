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
fun UVBlock(uvIndex: Double?, modifier: Modifier = Modifier) {
    val v = uvIndex?.let { it.toInt() }
    val label = when {
        v == null -> "—"
        v < 3 -> "Low"
        v < 6 -> "Moderate"
        v < 8 -> "High"
        v < 11 -> "Very high"
        else -> "Extreme"
    }
    WeatherBlock(
        title = "UV index",
        iconRes = R.drawable.outline_clear_day_24,
        modifier = modifier,
        shape = MaterialShapes.Sunny.toShape(),
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Text(
            v?.toString() ?: "—",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.align(Alignment.Center).offset(y = 4.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f),
            modifier = Modifier.align(Alignment.BottomCenter).offset(y = (-20).dp),
        )
    }
}
