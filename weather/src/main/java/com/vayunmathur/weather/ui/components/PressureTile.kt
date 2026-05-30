package com.vayunmathur.weather.ui.components

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vayunmathur.weather.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PressureBlock(pressureHpa: Double, modifier: Modifier = Modifier) {
    val inHg = pressureHpa * 0.02953
    WeatherBlock(
        title = "Pressure",
        iconRes = R.drawable.outline_pressure_24,
        modifier = modifier,
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(
            String.format("%.2f", inHg),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.Center).offset(y = 4.dp),
        )
        Text(
            "inHg",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.BottomCenter).offset(y = (-20).dp),
        )
    }
}
