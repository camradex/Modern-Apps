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
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VisibilityBlock(visibilityMeters: Double, useMiles: Boolean = false, modifier: Modifier = Modifier) {
    val value: String
    val unit: String
    if (useMiles) {
        value = (visibilityMeters / 1609.34).roundToInt().toString()
        unit = "mi"
    } else {
        value = (visibilityMeters / 1000).roundToInt().toString()
        unit = "km"
    }
    val label = when {
        visibilityMeters >= 16_000 -> "Crystal clear"
        visibilityMeters >= 8_000 -> "Clear"
        visibilityMeters >= 4_000 -> "Slight haze"
        visibilityMeters >= 1_000 -> "Hazy"
        else -> "Poor"
    }
    WeatherBlock(
        title = "Visibility",
        iconRes = R.drawable.outline_visibility_24,
        modifier = modifier,
        shape = MaterialShapes.Cookie12Sided.toShape(),
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Text(
            "$value $unit",
            style = MaterialTheme.typography.displaySmall,
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
