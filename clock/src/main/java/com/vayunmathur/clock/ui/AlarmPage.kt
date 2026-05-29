package com.vayunmathur.clock.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vayunmathur.library.util.NavBackStack
import com.vayunmathur.clock.util.AlarmScheduler
import com.vayunmathur.clock.mainPages
import com.vayunmathur.clock.R
import com.vayunmathur.clock.Route
import com.vayunmathur.clock.data.Alarm
import com.vayunmathur.library.ui.IconAdd
import com.vayunmathur.library.ui.IconDelete
import com.vayunmathur.clock.util.ClockViewModel
import com.vayunmathur.library.util.BottomNavBar
import com.vayunmathur.library.util.ResultEffect
import kotlinx.datetime.LocalTime
import kotlinx.datetime.format
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmPage(backStack: NavBackStack<Route>, clockViewModel: ClockViewModel, newAlarmParams: Route.NewAlarmDialog? = null) {
    val alarms by clockViewModel.alarms.collectAsState()
    val context = LocalContext.current
    val alarmScheduler = remember { AlarmScheduler.get() }
    ResultEffect<LocalTime>("alarm_time") {
        var daysMask = 0
        newAlarmParams?.days?.forEach { day ->
            daysMask = daysMask or (1 shl (day - 1))
        }
        val newAlarm = Alarm(it, newAlarmParams?.message ?: "", true, daysMask)
        clockViewModel.upsert(newAlarm) { id ->
            alarmScheduler.schedule(context, newAlarm.copy(id = id))
        }
    }
    Scaffold(topBar = {
        TopAppBar({Text(stringResource(R.string.label_alarm))})
    }, bottomBar = {
        BottomNavBar(backStack, mainPages(), Route.Alarm)
    }, floatingActionButton = {
        if (alarms.isNotEmpty()) {
            FloatingActionButton({
                backStack.add(Route.NewAlarmDialog())
            }) {
                IconAdd()
            }
        }
    }) { paddingValues ->
        if (alarms.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Button(onClick = { backStack.add(Route.NewAlarmDialog()) }) {
                    IconAdd()
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.set_an_alarm))
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = paddingValues.calculateTopPadding() + 8.dp,
                    bottom = paddingValues.calculateBottomPadding() + 80.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(alarms, key = { it.id }) { alarm ->
                    AlarmCard(backStack, alarm, clockViewModel, alarmScheduler)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AlarmCard(
    backStack: NavBackStack<Route>,
    alarm: Alarm,
    clockViewModel: ClockViewModel,
    alarmScheduler: AlarmScheduler
) {
    val context = LocalContext.current
    ResultEffect<LocalTime>("alarm_set_time_${alarm.id}") {
        val newAlarm = alarm.copy(time = it)
        if(newAlarm.enabled) {
            alarmScheduler.schedule(context, newAlarm)
        }
        clockViewModel.upsert(newAlarm)
    }
    Card(
        onClick = { backStack.add(Route.AlarmSetTimeDialog(alarm.id, alarm.time)) },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (alarm.enabled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    if (alarm.name.isNotEmpty()) {
                        Text(
                            alarm.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    val format = if(DateFormat.is24HourFormat(context)) {
                        LocalTime.Format {
                            hour(Padding.ZERO)
                            char(':')
                            minute()
                        }
                    } else {
                        LocalTime.Format {
                            amPmHour(Padding.NONE)
                            char(':')
                            minute()
                            amPmMarker(" AM", " PM")
                        }
                    }
                    Text(
                        alarm.time.format(format),
                        style = MaterialTheme.typography.displayMedium,
                        color = if (alarm.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = alarm.enabled, onCheckedChange = {
                        val newAlarm = alarm.copy(enabled = it)
                        if(newAlarm.enabled) {
                            alarmScheduler.schedule(context, newAlarm)
                        } else {
                            alarmScheduler.cancel(context, newAlarm)
                        }
                        clockViewModel.upsert(alarm.copy(enabled = it))
                    })
                    Spacer(Modifier.width(8.dp))
                    IconButton({
                        alarmScheduler.cancel(context, alarm)
                        clockViewModel.delete(alarm)
                    }) {
                        IconDelete()
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                "SMTWTFS".forEachIndexed { idx, day ->
                    val isSelected = alarm.days and (1 shl idx) != 0
                    ToggleButton(
                        checked = isSelected,
                        onCheckedChange = {
                            val newDays = if (isSelected) alarm.days and (1 shl idx).inv() else alarm.days or (1 shl idx)
                            val newAlarm = alarm.copy(days = newDays)
                            if(newAlarm.enabled) {
                                alarmScheduler.schedule(context, newAlarm)
                            }
                            clockViewModel.upsert(newAlarm)
                        }
                    ) {
                        Text(day.toString())
                    }
                }
            }
        }
    }
}
