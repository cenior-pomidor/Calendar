package io.github.ceniorpomidor.workcalendar.ui.calendar

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.ceniorpomidor.workcalendar.data.repo.CalcContext
import io.github.ceniorpomidor.workcalendar.domain.model.Absence
import io.github.ceniorpomidor.workcalendar.domain.model.AbsenceType
import io.github.ceniorpomidor.workcalendar.domain.model.Shift
import io.github.ceniorpomidor.workcalendar.domain.model.ShiftDisplayState
import io.github.ceniorpomidor.workcalendar.domain.model.ShiftKind
import io.github.ceniorpomidor.workcalendar.domain.model.ShiftStatus
import io.github.ceniorpomidor.workcalendar.domain.util.Formats
import io.github.ceniorpomidor.workcalendar.ui.components.ConfirmDialog
import io.github.ceniorpomidor.workcalendar.ui.components.DatePickerDialogCompat
import io.github.ceniorpomidor.workcalendar.ui.components.Tag
import io.github.ceniorpomidor.workcalendar.ui.components.TextInput
import io.github.ceniorpomidor.workcalendar.ui.components.money
import io.github.ceniorpomidor.workcalendar.ui.theme.AppIcons
import io.github.ceniorpomidor.workcalendar.ui.theme.AppTheme
import java.time.LocalDate
import java.time.LocalDateTime

/** Actions available for shifts in the day panel. */
class ShiftActions(
    val confirm: (Shift) -> Unit,
    val confirmFull: (Shift) -> Unit,
    val edit: (Shift) -> Unit,
    val move: (Shift, LocalDate) -> Unit,
    val cancel: (Shift) -> Unit,
    val restore: (Shift) -> Unit,
    val delete: (Shift) -> Unit,
    val missed: (Shift) -> Unit,
)

@Composable
fun DayPanel(
    info: DayInfo?,
    date: LocalDate,
    calc: CalcContext?,
    now: LocalDateTime,
    actions: ShiftActions,
    onAddShift: (extra: Boolean) -> Unit,
    onAddAbsence: (AbsenceType) -> Unit,
    onOpenAbsence: (Absence) -> Unit,
    onSaveNote: (String) -> Unit,
    alarms: DayAlarmsUi? = null,
) {
    var noteDialog by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Formats.dayTitle(date).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium)
                info?.holidayName?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = AppTheme.status.holiday)
                }
            }
            IconButton(onClick = { noteDialog = true }) { Icon(AppIcons.EditNote, contentDescription = "Заметка") }
        }

        info?.absence?.let { AbsenceCard(it, onClick = { onOpenAbsence(it) }) }

        val visible = info?.shifts.orEmpty().filter { it.status != ShiftStatus.COVERED || info?.absence == null }
        if (visible.isEmpty() && info?.absence == null) {
            Text(
                if (info?.specialDayOff == true) "Праздничный выходной" else "Выходной — смен нет",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        for (shift in visible) {
            ShiftCard(shift, calc, now, actions)
        }
        val covered = info?.shifts.orEmpty().filter { it.status == ShiftStatus.COVERED }
        if (covered.isNotEmpty() && info?.absence != null) {
            Text(
                "По графику была смена ${covered.joinToString { Formats.timeRange(it.plannedStart, it.plannedEnd) }} — сохранена в истории, в учёт не входит",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (alarms != null) DayAlarms(date, alarms)

        info?.note?.let { note ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = RoundedCornerShape(14.dp),
                onClick = { noteDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(12.dp)) {
                    Icon(AppIcons.Note, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(note, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val hasRegular = info?.shifts.orEmpty().any { it.isActive && it.kind == ShiftKind.REGULAR }
            AssistChip(
                onClick = { onAddShift(hasRegular || info?.absence != null) },
                label = { Text(if (hasRegular) "Доп. смена" else "Смена") },
                leadingIcon = { Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
            if (info?.absence == null) {
                AssistChip(
                    onClick = { onAddAbsence(AbsenceType.VACATION) },
                    label = { Text("Отпуск") },
                    leadingIcon = { Icon(AppIcons.Vacation, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
                AssistChip(
                    onClick = { onAddAbsence(AbsenceType.SICK) },
                    label = { Text("Больничный") },
                    leadingIcon = { Icon(AppIcons.Medical, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
                AssistChip(
                    onClick = { onAddAbsence(AbsenceType.OTHER) },
                    label = { Text("Другое") },
                    leadingIcon = { Icon(AppIcons.EventBusy, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }
        Spacer(Modifier.padding(bottom = 80.dp))
    }

    if (noteDialog) {
        var text by rememberSaveable { mutableStateOf(info?.note.orEmpty()) }
        AlertDialog(
            onDismissRequest = { noteDialog = false },
            title = { Text("Заметка · ${Formats.shortDate(date)}") },
            text = { TextInput("Текст заметки", text, { text = it }, singleLine = false, minLines = 3) },
            confirmButton = {
                TextButton(onClick = {
                    onSaveNote(text)
                    noteDialog = false
                }) { Text("Сохранить") }
            },
            dismissButton = {
                if (!info?.note.isNullOrEmpty()) {
                    TextButton(onClick = {
                        onSaveNote("")
                        noteDialog = false
                    }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                } else {
                    TextButton(onClick = { noteDialog = false }) { Text("Отмена") }
                }
            },
        )
    }
}

@Composable
private fun AbsenceCard(absence: Absence, onClick: () -> Unit) {
    val p = AppTheme.status
    val (bg, fg) = when (absence.type) {
        AbsenceType.VACATION, AbsenceType.UNPAID -> p.vacation to p.onVacation
        AbsenceType.SICK -> p.sick to p.onSick
        AbsenceType.OTHER -> p.otherAbsence to p.onOtherAbsence
    }
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = bg, contentColor = fg),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (absence.type == AbsenceType.SICK) AppIcons.Medical else AppIcons.Vacation, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(absence.displayTitle(), style = MaterialTheme.typography.titleSmall)
                Text("${Formats.period(absence.startDate, absence.endDate)} · ${Formats.days(absence.calendarDays)}", style = MaterialTheme.typography.bodySmall)
                val amount = absence.amount
                Text(
                    when {
                        !absence.paid -> "Без оплаты"
                        amount != null -> "Сумма: ${money(amount)}" + if (absence.manualAmount == null && absence.calculation?.insufficientData == true) " (неточно)" else ""
                        else -> "Сумма не рассчитана"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Icon(AppIcons.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
fun ShiftCard(shift: Shift, calc: CalcContext?, now: LocalDateTime, actions: ShiftActions) {
    val context = LocalContext.current
    val display = shift.displayState(now)
    val (bg, fg) = display.colors(AppTheme.status)
    var menu by remember { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf<String?>(null) }
    var moving by remember { mutableStateOf(false) }
    val zone = calc?.zone ?: java.time.ZoneId.systemDefault()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tag(display.title(), bg, fg)
                if (shift.kind == ShiftKind.EXTRA) {
                    Spacer(Modifier.width(6.dp))
                    Tag("Доп.", AppTheme.status.extra, AppTheme.status.onExtra)
                }
                Spacer(Modifier.weight(1f))
                Box {
                    IconButton(onClick = { menu = true }) { Icon(AppIcons.MoreVert, contentDescription = "Действия") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (shift.status == ShiftStatus.PLANNED || shift.isClosed) {
                            DropdownMenuItem(text = { Text("Изменить") }, leadingIcon = { Icon(AppIcons.Edit, null) }, onClick = {
                                menu = false
                                actions.edit(shift)
                            })
                        }
                        if (shift.status == ShiftStatus.PLANNED) {
                            DropdownMenuItem(text = { Text("Перенести на другую дату") }, leadingIcon = { Icon(AppIcons.Swap, null) }, onClick = {
                                menu = false
                                moving = true
                            })
                            DropdownMenuItem(text = { Text("Не состоялась") }, leadingIcon = { Icon(AppIcons.DoNotDisturb, null) }, onClick = {
                                menu = false
                                confirmAction = "missed"
                            })
                            DropdownMenuItem(text = { Text("Отменить смену") }, leadingIcon = { Icon(AppIcons.EventBusy, null) }, onClick = {
                                menu = false
                                confirmAction = "cancel"
                            })
                        }
                        if (shift.status == ShiftStatus.CANCELLED) {
                            DropdownMenuItem(text = { Text("Восстановить") }, leadingIcon = { Icon(AppIcons.Restore, null) }, onClick = {
                                menu = false
                                actions.restore(shift)
                            })
                        }
                        if (shift.status == ShiftStatus.PLANNED || shift.status == ShiftStatus.CONFIRMED) {
                            DropdownMenuItem(text = { Text("В системный календарь") }, leadingIcon = { Icon(AppIcons.EventAvailable, null) }, onClick = {
                                menu = false
                                addToSystemCalendar(context, shift, zone)
                            })
                        }
                        if (!shift.isClosed && shift.status != ShiftStatus.MOVED) {
                            DropdownMenuItem(text = { Text("Удалить") }, leadingIcon = { Icon(AppIcons.Delete, null) }, onClick = {
                                menu = false
                                confirmAction = "delete"
                            })
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(Formats.timeRange(shift.plannedStart, shift.plannedEnd), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                if (shift.title.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(shift.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val planned = shift.plannedPaidMinutes(zone)
            val breakText = if (shift.plannedBreakMinutes > 0) ", перерыв ${shift.plannedBreakMinutes} мин" else ""
            Text("План: ${Formats.hoursMinutes(planned)}$breakText", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when (shift.status) {
                ShiftStatus.CONFIRMED -> {
                    val pay = shift.pay ?: calc?.pay?.calculate(shift, shift.workedMinutes ?: 0)
                    Text(
                        "Факт: ${Formats.hoursMinutes((shift.workedMinutes ?: 0).toLong())}" + (pay?.let { " · ${money(it.total)}" } ?: " · ставка не задана"),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                }
                ShiftStatus.MOVED -> shift.movedToDate?.let { Text("Перенесена на ${Formats.longDate(it, withYear = false)}", style = MaterialTheme.typography.bodyMedium) }
                else -> Unit
            }
            shift.movedFromDate?.let { Text("Перенесена с ${Formats.longDate(it, withYear = false)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            shift.hourlyRateOverride?.let { Text("Индивидуальная ставка: ${money(it)}/ч", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (shift.note.isNotBlank()) Text(shift.note, style = MaterialTheme.typography.bodySmall)
            when (display) {
                ShiftDisplayState.AWAITING_CONFIRMATION, ShiftDisplayState.IN_PROGRESS, ShiftDisplayState.UPCOMING -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(end = 10.dp)) {
                        if (display == ShiftDisplayState.AWAITING_CONFIRMATION) {
                            Button(onClick = { actions.confirmFull(shift) }) { Text("Полностью (${Formats.hoursDecimal(planned)} ч)") }
                            OutlinedButton(onClick = { actions.confirm(shift) }) { Text("Другое") }
                        } else {
                            FilledTonalButton(onClick = { actions.confirm(shift) }) { Text("Отметить часы") }
                            OutlinedButton(onClick = { actions.edit(shift) }) { Text("Изменить") }
                        }
                    }
                }
                ShiftDisplayState.CONFIRMED, ShiftDisplayState.MISSED -> {
                    TextButton(onClick = { actions.confirm(shift) }) { Text("Подробнее / исправить") }
                }
                else -> Unit
            }
        }
    }

    when (confirmAction) {
        "cancel" -> ConfirmDialog(
            title = "Отменить смену?",
            text = "Смена ${Formats.shortDate(shift.date)} останется в календаре как отменённая и не войдёт в учёт. Остальные даты и шаблон графика не изменятся.",
            confirmText = "Отменить смену",
            dismissText = "Назад",
            onConfirm = { actions.cancel(shift) },
            onDismiss = { confirmAction = null },
        )
        "delete" -> ConfirmDialog(
            title = "Удалить смену?",
            text = "Смена ${Formats.shortDate(shift.date)} будет удалена. Если она создана по графику, повторно она не появится.",
            confirmText = "Удалить",
            destructive = true,
            onConfirm = { actions.delete(shift) },
            onDismiss = { confirmAction = null },
        )
        "missed" -> ConfirmDialog(
            title = "Смена не состоялась?",
            text = "Смена будет отмечена как несостоявшаяся: 0 часов, без начислений.",
            confirmText = "Отметить",
            onConfirm = { actions.missed(shift) },
            onDismiss = { confirmAction = null },
        )
    }
    if (moving) {
        DatePickerDialogCompat(shift.date.plusDays(1), onDismiss = { moving = false }) { newDate ->
            moving = false
            actions.move(shift, newDate)
        }
    }
}

private fun addToSystemCalendar(context: Context, shift: Shift, zone: java.time.ZoneId) {
    val intent = Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, shift.plannedStart.atZone(zone).toInstant().toEpochMilli())
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, shift.plannedEnd.atZone(zone).toInstant().toEpochMilli())
        putExtra(CalendarContract.Events.TITLE, if (shift.kind == ShiftKind.EXTRA) "Доп. смена" else "Смена")
        putExtra(CalendarContract.Events.DESCRIPTION, shift.note)
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Не найдено приложение календаря", Toast.LENGTH_SHORT).show()
    }
}
