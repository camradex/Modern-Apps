package com.vayunmathur.weather.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vayunmathur.library.ui.IconNavigation
import com.vayunmathur.library.util.NavBackStack
import com.vayunmathur.weather.Route
import com.vayunmathur.weather.util.TemperatureUnit
import com.vayunmathur.weather.util.WeatherViewModel
import com.vayunmathur.weather.util.WindUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(backStack: NavBackStack<Route>, viewModel: WeatherViewModel) {
    val tempUnit by viewModel.tempUnit.collectAsState()
    val windUnit by viewModel.windUnit.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconNavigation(backStack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Temperature",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = tempUnit == TemperatureUnit.Celsius,
                    onClick = { viewModel.setTempUnit(TemperatureUnit.Celsius) },
                    label = { Text("°C") },
                )
                FilterChip(
                    selected = tempUnit == TemperatureUnit.Fahrenheit,
                    onClick = { viewModel.setTempUnit(TemperatureUnit.Fahrenheit) },
                    label = { Text("°F") },
                )
            }

            Text(
                "Wind",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 16.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = windUnit == WindUnit.KmH,
                    onClick = { viewModel.setWindUnit(WindUnit.KmH) },
                    label = { Text("km/h") },
                )
                FilterChip(
                    selected = windUnit == WindUnit.Mph,
                    onClick = { viewModel.setWindUnit(WindUnit.Mph) },
                    label = { Text("mph") },
                )
            }
        }
    }
}
