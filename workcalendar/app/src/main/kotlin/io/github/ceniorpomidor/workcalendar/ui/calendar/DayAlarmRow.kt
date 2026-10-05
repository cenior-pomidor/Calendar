package io.github.ceniorpomidor.workcalendar.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.ceniorpomidor.workcalendar.domain.alarm.PlannedAlarm
import io.github.ceniorpomidor.workcalendar.domain.time.TimeMath
import io.github.ceniorpomidor.workcalendar.domain.util.Formats
import io.github.ceniorpomidor.workcalendar.ui.theme.AppIcons
import java.time.LocalDate

/** Alarm of the selected day and what can be done with it in the day panel. */
class DayAlarmUi(
    /** What rings for this day; null — nothing. */
    val effective: PlannedAlarm?,
    /** Working day alarm without the change made for this day. */
    val regular: PlannedAlarm?,
    /** The alarm of this day was changed by the user. */
    val changed: Boolean,
    /** Time offered when the alarm is set for this day only, minute of the day. */
    val suggestedMinute: Int,
    val onSet: (minute: Int) -> Unit,
    val onOff: () -> Unit,
    val onReset: () -> Unit,
)

@Composable
fun DayAlarmRow(date: LocalDate, alarm: DayAlarmUi) {
    var picking by rememberSaveable(date) { mutableStateOf(false) }
    val effective = alarm.effective
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { picking = true }
            .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (effective != null) AppIcons.Alarm else AppIcons.AlarmOff,
            contentDescription = null,
            tint = if (effective != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    effective == null && alarm.regular != null -> "Будильник выключен"
                    effective == null -> "Будильник не заведён"
                    effective.at.toLocalDate() != date -> "Будильник ${Formats.time(effective.at)} накануне"
                    else -> "Будильник ${Formats.time(effective.at)}"
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                when {
                    effective == null && alarm.regular != null -> "Только в этот день, в остальные рабочие дни зазвонит"
                    effective == null -> "Включите, чтобы разбудить в этот день"
                    effective.single -> "Только в этот день"
                    else -> "Как во все рабочие дни"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = effective != null,
            onCheckedChange = { checked ->
                when {
                    !checked -> alarm.onOff()
                    // The day was switched off: back to the working day alarm.
                    alarm.regular != null -> alarm.onReset()
                    else -> picking = true
                }
            },
        )
    }
    if (picking) {
        val initial = effective?.at?.takeIf { it.toLocalDate() == date }?.let { TimeMath.minuteOfDay(it.toLocalTime()) } ?: alarm.suggestedMinute
        AlarmTimeDialog(
            date = date,
            initialMinute = initial,
            canReset = alarm.changed && alarm.regular != null,
            onReset = {
                alarm.onReset()
                picking = false
            },
            onDismiss = { picking = false },
            onPicked = {
                alarm.onSet(it)
                picking = false
            },
        )
    }
}

@Composable
private fun AlarmTimeDialog(date: LocalDate, initialMinute: Int, canReset: Boolean, onReset: () -> Unit, onDismiss: () -> Unit, onPicked: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = initialMinute / 60 % 24, initialMinute = initialMinute % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Будильник на ${Formats.shortDate(date)}") },
        text = {
            Column {
                TimePicker(state = state)
                Text(
                    "Только в этот день. Будильник во все рабочие дни — в разделе «Будильник».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onPicked(state.hour * 60 + state.minute) }) { Text("Готово") } },
        dismissButton = {
            Row {
                if (canReset) TextButton(onClick = onReset) { Text("Как обычно") }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}
