package com.vayunmathur.weather.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Square weather-detail block, modelled on WeatherMaster's `WeatherBlocks`
 * cookie-shaped tiles. Each block picks its own [shape] from
 * [MaterialShapes] and its own [containerColor] from [MaterialTheme.colorScheme]
 * so the grid feels varied while staying inside the seed-derived Material
 * You palette. Header sits at top-center, content fills the rest as a
 * [BoxScope].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WeatherBlock(
    title: String,
    iconRes: Int,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialShapes.Cookie12Sided.toShape(),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .aspectRatio(1f),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
            Box(modifier = Modifier.align(Alignment.TopCenter)) {
                BlockHeader(title = title, iconRes = iconRes, contentColor = contentColor)
            }
        }
    }
}

@Composable
private fun BlockHeader(title: String, iconRes: Int, contentColor: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp, alignment = Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 16.dp, start = 12.dp, end = 12.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = contentColor.copy(alpha = 0.9f),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = contentColor.copy(alpha = 0.9f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
