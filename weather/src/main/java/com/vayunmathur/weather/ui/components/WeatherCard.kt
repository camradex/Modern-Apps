package com.vayunmathur.weather.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Header rendered ABOVE each section's surface — `titleMedium` `Medium`,
 * with a small leading glyph in the same color. Mirrors the Pixel Weather
 * card-title look ("Hourly forecast" with a clock icon, "10-day forecast"
 * with a calendar icon, etc.).
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    leadingIconRes: Int? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIconRes != null) {
            Icon(
                painter = painterResource(id = leadingIconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Section shell — header inside the card at the top, then content
 * underneath. Matches Pixel Weather's card-with-header-inside pattern.
 * Background is `surfaceContainer` for a subtle elevation against the page
 * surface.
 */
@Composable
fun WeatherSection(
    title: String,
    modifier: Modifier = Modifier,
    leadingIconRes: Int? = null,
    surfacePadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            SectionHeader(title = title, leadingIconRes = leadingIconRes)
            Column(
                modifier = Modifier.fillMaxWidth().padding(surfacePadding),
                content = content,
            )
        }
    }
}

/** Thin inset hairline between rows inside a [WeatherSection]. */
@Composable
fun WeatherSectionDivider(insetStart: Dp = 16.dp) {
    HorizontalDivider(
        modifier = Modifier.padding(start = insetStart, end = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}
