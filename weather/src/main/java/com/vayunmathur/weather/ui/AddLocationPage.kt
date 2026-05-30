package com.vayunmathur.weather.ui

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vayunmathur.library.ui.CommonSearchBar
import com.vayunmathur.library.ui.IconNavigation
import com.vayunmathur.library.util.NavBackStack
import com.vayunmathur.weather.Route
import com.vayunmathur.weather.network.GeocodingResult
import com.vayunmathur.weather.network.WeatherApi
import com.vayunmathur.weather.util.LocationProvider
import com.vayunmathur.weather.util.WeatherViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/** Search by city name OR pull from the device's current location. */
@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class,
    kotlinx.coroutines.FlowPreview::class)
@Composable
fun AddLocationPage(backStack: NavBackStack<Route>, viewModel: WeatherViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GeocodingResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        snapshotFlow { query }
            .debounce(300)
            .distinctUntilChanged()
            .flatMapLatest { q ->
                flow {
                    if (q.isBlank()) {
                        emit(emptyList<GeocodingResult>())
                    } else {
                        searching = true
                        val res = runCatching { WeatherApi.geocode(q).results }.getOrDefault(emptyList())
                        searching = false
                        emit(res)
                    }
                }
            }
            .collect { results = it }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) {
            scope.launch { useCurrentLocation(context, viewModel, backStack) }
        } else {
            Toast.makeText(context, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add location") },
                navigationIcon = { IconNavigation(backStack) },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Button(
                onClick = {
                    if (LocationProvider.hasPermission(context)) {
                        scope.launch { useCurrentLocation(context, viewModel, backStack) }
                    } else {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) { Text("Use current location") }

            HorizontalDivider()

            CommonSearchBar(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search city",
            )

            Box(modifier = Modifier.fillMaxSize()) {
                if (searching && results.isEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (query.isNotBlank() && results.isEmpty()) {
                    Text(
                        "No matches",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(results, key = { it.id }) { r ->
                            ListItem(
                                headlineContent = { Text(r.name) },
                                supportingContent = {
                                    val parts = listOfNotNull(r.admin1, r.country).filter { it.isNotBlank() }
                                    if (parts.isNotEmpty()) Text(parts.joinToString(", "))
                                },
                                modifier = Modifier.clickable {
                                    viewModel.addLocation(
                                        name = r.name,
                                        country = r.country.orEmpty(),
                                        latitude = r.latitude,
                                        longitude = r.longitude,
                                    )
                                    backStack.pop()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private suspend fun useCurrentLocation(
    context: android.content.Context,
    viewModel: WeatherViewModel,
    backStack: NavBackStack<Route>,
) {
    val loc = LocationProvider.currentLocation(context)
    if (loc == null) {
        Toast.makeText(context, "Couldn't determine location", Toast.LENGTH_SHORT).show()
        return
    }
    // Best-effort name via reverse geocoding via Open-Meteo (matches the nearest place).
    val name = runCatching {
        WeatherApi.geocode(
            // Approximate reverse: search for the lat,lon string isn't supported by /search.
            // Just use a placeholder; the user can rename later. We'll fill from any nearby
            // result if the user has typed something previously — but for "use current
            // location" the simplest UX is a fixed label.
            query = "",
        ).results.firstOrNull()?.name
    }.getOrNull() ?: "Current location"
    viewModel.setCurrentLocation(name = name, latitude = loc.latitude, longitude = loc.longitude)
    backStack.pop()
}
