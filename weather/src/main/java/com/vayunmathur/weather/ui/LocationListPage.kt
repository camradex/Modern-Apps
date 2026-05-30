package com.vayunmathur.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vayunmathur.library.ui.IconAdd
import com.vayunmathur.library.ui.IconDelete
import com.vayunmathur.library.ui.IconNavigation
import com.vayunmathur.library.util.NavBackStack
import com.vayunmathur.weather.R
import com.vayunmathur.weather.Route
import com.vayunmathur.weather.data.SavedLocation
import com.vayunmathur.weather.util.TemperatureUnit
import com.vayunmathur.weather.util.WeatherViewModel
import com.vayunmathur.weather.util.formatTemperatureCompact
import com.vayunmathur.weather.util.weatherConditionForCode

/**
 * Manage saved locations — pill-shaped cards mirroring the Pixel Weather
 * screen. Current device row gets a "Current location" section header with
 * a target icon; user-added rows go under "Saved locations" with a pin icon.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationListPage(backStack: NavBackStack<Route>, viewModel: WeatherViewModel) {
    val locations by viewModel.savedLocations.collectAsState()
    val (currentDevice, saved) = locations.partition { it.isCurrent }

    LaunchedEffect(locations) {
        locations.forEach { viewModel.ensureForecast(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weather") },
                navigationIcon = { IconNavigation(backStack) },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { backStack.add(Route.AddLocation) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(20.dp),
            ) { IconAdd() }
        },
    ) { padding ->
        if (locations.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "No locations yet. Tap + to add one.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (currentDevice.isNotEmpty()) {
                item {
                    SmallSectionLabel(
                        text = "Current location",
                        iconRes = R.drawable.outline_my_location_24,
                    )
                }
                items(currentDevice, key = { it.id }) { loc ->
                    LocationPillCard(loc, viewModel) { viewModel.deleteLocation(loc) }
                }
            }
            if (saved.isNotEmpty()) {
                item {
                    SmallSectionLabel(
                        text = "Saved locations",
                        iconRes = R.drawable.outline_location_on_24,
                    )
                }
                items(saved, key = { it.id }) { loc ->
                    LocationPillCard(loc, viewModel) { viewModel.deleteLocation(loc) }
                }
            }
        }
    }
}

@Composable
private fun SmallSectionLabel(text: String, iconRes: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Pill-shaped card with a circular condition glyph, location name, condition
 * + hi/lo subtitle, and big temperature on the right. Long-press could
 * delete; for v1 we just expose a trailing delete button.
 */
@Composable
private fun LocationPillCard(
    location: SavedLocation,
    viewModel: WeatherViewModel,
    onDelete: () -> Unit,
) {
    val forecasts by viewModel.forecasts.collectAsState()
    val tempUnit by viewModel.tempUnit.collectAsState()
    val state = forecasts[location.id]
    val current = state?.forecast?.current
    val daily = state?.forecast?.daily
    val condition = current?.weatherCode?.let { weatherConditionForCode(it) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(28.dp))
            .clickable { /* tap → could navigate to that page; for now just no-op */ },
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                if (condition != null) {
                    Icon(
                        painter = painterResource(condition.iconRes(current?.isDay == 1)),
                        contentDescription = condition.label,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    location.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                if (condition != null) {
                    Text(
                        condition.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (daily != null) {
                    val hi = daily.temperatureMax.firstOrNull()
                    val lo = daily.temperatureMin.firstOrNull()
                    if (hi != null && lo != null) {
                        Text(
                            "${formatTemperatureCompact(hi, tempUnit)}  ${formatTemperatureCompact(lo, tempUnit)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (current != null) {
                Text(
                    formatTemperatureCompact(current.temperature, tempUnit),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Light,
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                IconDelete()
            }
        }
    }
}
