package com.vayunmathur.weather.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vayunmathur.weather.network.AirQualityCurrent
import com.vayunmathur.weather.network.Current
import com.vayunmathur.weather.network.Daily
import com.vayunmathur.weather.util.TemperatureUnit
import com.vayunmathur.weather.util.WeatherTile
import com.vayunmathur.weather.util.WeatherViewModel
import com.vayunmathur.weather.util.WindUnit
import sh.calvin.reorderable.DragGestureDetector
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

/**
 * Adaptive grid of WeatherMaster-style cookie blocks, draggable via
 * `sh.calvin.reorderable` long-press. Mirrors WeatherMaster's `WeatherBlocks`
 * layout: `GridCells.Adaptive(140.dp)`, 14dp spacing, `userScrollEnabled = false`,
 * `heightIn(max = 1500.dp)` so the grid plays nicely inside the parent
 * vertical `LazyColumn`.
 */
@Composable
fun WeatherBlocksGrid(
    current: Current,
    today: Daily?,
    air: AirQualityCurrent?,
    sunriseEpochSec: Long?,
    sunsetEpochSec: Long?,
    tempUnit: TemperatureUnit,
    windUnit: WindUnit,
    viewModel: WeatherViewModel,
    modifier: Modifier = Modifier,
) {
    val savedOrder by viewModel.tileOrder.collectAsState()
    val items = remember(savedOrder) { mutableStateListOf<WeatherTile>().apply { addAll(savedOrder) } }
    LaunchedEffect(savedOrder) {
        if (items.toList() != savedOrder) {
            items.clear(); items.addAll(savedOrder)
        }
    }

    val gridState = rememberLazyGridState()
    val reorderableState = rememberReorderableLazyGridState(gridState) { from, to ->
        items.add(to.index, items.removeAt(from.index))
        viewModel.setTileOrder(items.toList())
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 140.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .heightIn(max = 1500.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 8.dp),
        userScrollEnabled = false,
    ) {
        items(items = items, key = { it.name }) { tile ->
            ReorderableItem(reorderableState, key = tile.name) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .draggableHandle(dragGestureDetector = DragGestureDetector.LongPress),
                ) {
                    when (tile) {
                        WeatherTile.AirQuality -> AirQualityBlock(aqi = air?.usAqi)
                        WeatherTile.Sun -> SunBlock(
                            sunriseEpochSec = sunriseEpochSec,
                            sunsetEpochSec = sunsetEpochSec,
                        )
                        WeatherTile.Visibility -> VisibilityBlock(visibilityMeters = current.visibility)
                        WeatherTile.Pressure -> PressureBlock(pressureHpa = current.pressureMsl)
                        WeatherTile.Humidity -> HumidityBlock(
                            relativeHumidity = current.relativeHumidity,
                            dewPointCelsius = current.dewPoint,
                            tempUnit = tempUnit,
                        )
                        WeatherTile.Pollen -> PollenBlock(air = air)
                        WeatherTile.UV -> UVBlock(uvIndex = today?.uvIndexMax?.firstOrNull())
                        WeatherTile.Wind -> WindBlock(
                            speedKph = current.windSpeed,
                            directionDeg = current.windDirection,
                            unit = windUnit,
                        )
                    }
                }
            }
        }
    }
}
