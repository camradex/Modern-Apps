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
import com.vayunmathur.weather.network.AirQualityCurrent

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PollenBlock(air: AirQualityCurrent?, modifier: Modifier = Modifier) {
    val worst = listOfNotNull(
        air?.grassPollen,
        air?.alderPollen,
        air?.birchPollen,
        air?.olivePollen,
        air?.ragweedPollen,
        air?.mugwortPollen,
    ).maxOrNull() ?: 0.0
    val level = when {
        worst <= 0.0 -> 0
        worst < 10 -> 1
        worst < 50 -> 2
        worst < 200 -> 3
        else -> 4
    }
    val label = when (level) {
        0 -> "None"
        1 -> "Low"
        2 -> "Medium"
        3 -> "High"
        else -> "Severe"
    }
    WeatherBlock(
        title = "Pollen",
        iconRes = R.drawable.outline_grass_24,
        modifier = modifier,
        shape = MaterialShapes.Cookie12Sided.toShape(),
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Text(
            "$level/4",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.align(Alignment.Center).offset(y = 4.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.75f),
            modifier = Modifier.align(Alignment.BottomCenter).offset(y = (-20).dp),
        )
    }
}
