package com.vayunmathur.health.ui

import android.content.Context
import com.vayunmathur.health.R
import kotlinx.serialization.json.Json

/** Shared JSON formatter for FHIR / Health payloads. */
val JSON = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
}

/** Formats a minute count as "Xh Ym" or "Ym". */
fun hoursMinutesString(context: Context, minutes: Long): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) context.getString(R.string.hours_minutes_format, h, m)
    else context.getString(R.string.minutes_format, m)
}
