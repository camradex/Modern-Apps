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
import com.vayunmathur.weather.util.WindUnit
import com.vayunmathur.weather.util.compassDirection
import com.vayunmathur.weather.util.formatWind

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WindBlock(speedKph: Double, directionDeg: Int, unit: WindUnit, modifier: Modifier = Modifier) {
    WeatherBlock(
        title = "Wind",
        iconRes = R.drawable.outline_wind_24,
        modifier = modifier,
        shape = MaterialShapes.Pill.toShape(),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            formatWind(speedKph, unit),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.align(Alignment.Center).offset(y = 4.dp),
        )
        Text(
            "From ${compassDirection(directionDeg)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
            modifier = Modifier.align(Alignment.BottomCenter).offset(y = (-20).dp),
        )
    }
}
