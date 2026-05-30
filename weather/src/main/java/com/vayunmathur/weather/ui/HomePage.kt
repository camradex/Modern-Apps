package com.vayunmathur.weather.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vayunmathur.library.ui.IconAdd
import com.vayunmathur.library.ui.IconSettings
import com.vayunmathur.library.util.NavBackStack
import com.vayunmathur.weather.Route
import com.vayunmathur.weather.data.SavedLocation
import com.vayunmathur.weather.ui.components.CurrentHero
import com.vayunmathur.weather.ui.components.DailyList
import com.vayunmathur.weather.ui.components.HourlyStrip
import com.vayunmathur.weather.ui.components.PrecipChart
import com.vayunmathur.weather.ui.components.SunriseSunsetCard
import com.vayunmathur.weather.ui.components.WeatherBlocksGrid
import com.vayunmathur.weather.util.WeatherViewModel
import kotlin.time.Instant

/**
 * Pixel-Weather-inspired home page rendered against the standard Material
 * surface (no condition tinting). Layout follows the Health app's rhythm:
 *   - Top app bar with the location name centered
 *   - Hero block (no card) with the current temp + condition + Hi/Lo pill
 *   - A stack of sectioned cards: HOURLY → DETAILS → PRECIPITATION → SUN → 7-DAY
 *
 * Horizontal pager swipes between saved locations; small dot indicators in
 * the top app bar show which page you're on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePage(backStack: NavBackStack<Route>, viewModel: WeatherViewModel) {
    val locations by viewModel.savedLocations.collectAsState()

    if (locations.isEmpty()) {
        EmptyHome(onAddLocation = { backStack.add(Route.AddLocation) })
        return
    }

    val pagerState = rememberPagerState(initialPage = 0) { locations.size }
    val current = locations.getOrNull(pagerState.currentPage)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = current?.name.orEmpty(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (locations.size > 1) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 4.dp),
                            ) {
                                LocationDots(locations.size, pagerState.currentPage)
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { backStack.add(Route.Locations) }) { IconAdd() }
                    IconButton(onClick = { backStack.add(Route.Settings) }) { IconSettings() }
                },
            )
        },
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) { pageIndex ->
            LocationPage(viewModel = viewModel, location = locations[pageIndex])
        }
    }
}

@Composable
private fun LocationDots(count: Int, selectedIndex: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            val selected = i == selectedIndex
            Surface(
                modifier = Modifier
                    .size(if (selected) 7.dp else 5.dp)
                    .clip(CircleShape),
                color = if (selected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.outlineVariant,
                content = {},
            )
        }
    }
}

@Composable
private fun LocationPage(viewModel: WeatherViewModel, location: SavedLocation) {
    val forecasts by viewModel.forecasts.collectAsState()
    val tempUnit by viewModel.tempUnit.collectAsState()
    val windUnit by viewModel.windUnit.collectAsState()

    LaunchedEffect(location.id) { viewModel.ensureForecast(location) }

    val state = forecasts[location.id]
    val forecast = state?.forecast

    if (forecast == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (state?.error != null) {
                Text(state.error, color = MaterialTheme.colorScheme.error)
            } else {
                CircularProgressIndicator()
            }
        }
        return
    }

    val current = forecast.current
    val daily = forecast.daily
    val sunriseEpoch = daily?.sunrise?.firstOrNull()?.let { parseLocalIsoToEpochSec(it, forecast.utcOffsetSeconds) }
    val sunsetEpoch = daily?.sunset?.firstOrNull()?.let { parseLocalIsoToEpochSec(it, forecast.utcOffsetSeconds) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (current != null) {
            item { CurrentHero(location, current, daily, tempUnit) }
        }
        if (forecast.hourly != null) {
            item { HourlyStrip(forecast.hourly, tempUnit) }
        }
        if (current != null) {
            item {
                WeatherBlocksGrid(
                    current = current,
                    today = daily,
                    air = state.airQuality?.current,
                    sunriseEpochSec = sunriseEpoch,
                    sunsetEpochSec = sunsetEpoch,
                    tempUnit = tempUnit,
                    windUnit = windUnit,
                    viewModel = viewModel,
                )
            }
        }
        if (forecast.hourly != null) {
            item { PrecipChart(forecast.hourly) }
        }
        if (daily != null) {
            item { DailyList(daily, tempUnit) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmptyHome(onAddLocation: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Weather") }) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No locations yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Add a city or use your current location to see the forecast.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                )
                Button(onClick = onAddLocation) { Text("Add a location") }
            }
        }
    }
}

/**
 * Open-Meteo returns ISO local-time strings (no offset) when timezone=auto is
 * requested. Combine with the response's `utc_offset_seconds` to produce an
 * actual epoch.
 */
private fun parseLocalIsoToEpochSec(iso: String, utcOffsetSec: Int): Long? {
    val padded = if (iso.length == 16) "$iso:00" else iso
    return runCatching {
        Instant.parse("${padded}Z").epochSeconds - utcOffsetSec
    }.getOrNull()
}
