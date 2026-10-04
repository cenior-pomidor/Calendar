package io.github.ceniorpomidor.workcalendar.ui.calendar

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.OutDateStyle
import com.kizitonwose.calendar.core.daysOfWeek
import io.github.ceniorpomidor.workcalendar.AppContainer
import io.github.ceniorpomidor.workcalendar.domain.model.AbsenceType
import io.github.ceniorpomidor.workcalendar.domain.model.Shift
import io.github.ceniorpomidor.workcalendar.domain.pay.PeriodSummary
import io.github.ceniorpomidor.workcalendar.domain.util.Formats
import io.github.ceniorpomidor.workcalendar.ui.components.Banner
import io.github.ceniorpomidor.workcalendar.ui.components.BannerKind
import io.github.ceniorpomidor.workcalendar.ui.components.LocalSnackbar
import io.github.ceniorpomidor.workcalendar.ui.components.launchSafely
import io.github.ceniorpomidor.workcalendar.ui.components.money
import io.github.ceniorpomidor.workcalendar.ui.theme.AppIcons
import io.github.ceniorpomidor.workcalendar.ui.theme.AppTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun CalendarScreen(
    container: AppContainer,
    initialDate: LocalDate?,
    onConfirmShift: (Long) -> Unit,
    onEditShift: (Long) -> Unit,
    onNewShift: (LocalDate, Boolean) -> Unit,
    onNewAbsence: (AbsenceType, LocalDate) -> Unit,
    onOpenAbsence: (Long) -> Unit,
    onOpenUnconfirmed: () -> Unit,
    onOpenFinance: () -> Unit,
    onSearch: () -> Unit,
    onSetupSchedule: () -> Unit,
) {
    val vm: CalendarViewModel = viewModel { CalendarViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val selected by vm.selectedDate.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val today = container.clock.today()
    val currentMonth = remember { YearMonth.from(today) }
    var legend by rememberSaveable { mutableStateOf(false) }

    val calendarState = rememberCalendarState(
        startMonth = currentMonth.minusMonths(120),
        endMonth = currentMonth.plusMonths(60),
        firstVisibleMonth = initialDate?.let { YearMonth.from(it) } ?: currentMonth,
        firstDayOfWeek = state.settings.calendar.firstDayOfWeek,
        outDateStyle = OutDateStyle.EndOfGrid,
    )
    LaunchedEffect(initialDate) {
        if (initialDate != null) {
            vm.select(initialDate)
            calendarState.scrollToMonth(YearMonth.from(initialDate))
        }
    }
    LaunchedEffect(calendarState) {
        snapshotFlow { calendarState.firstVisibleMonth.yearMonth }.distinctUntilChanged().collect { vm.onMonthVisible(it) }
    }
    val visibleMonth = calendarState.firstVisibleMonth.yearMonth

    val actions = ShiftActions(
        confirm = { onConfirmShift(it.id) },
        confirmFull = { shift: Shift ->
            scope.launchSafely(snackbar, success = "Часы сохранены") { container.shifts.confirmAsPlanned(shift.id) }
        },
        edit = { onEditShift(it.id) },
        move = { shift, date -> scope.launchSafely(snackbar, success = "Смена перенесена на ${Formats.shortDate(date)}") { container.shifts.move(shift.id, date) } },
        cancel = { scope.launchSafely(snackbar, success = "Смена отменена") { container.shifts.cancel(it.id) } },
        restore = { scope.launchSafely(snackbar, success = "Смена восстановлена") { container.shifts.restore(it.id) } },
        delete = { scope.launchSafely(snackbar, success = "Смена удалена") { container.shifts.delete(it.id) } },
        missed = { scope.launchSafely(snackbar) { container.shifts.markMissed(it.id) } },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(Formats.monthTitle(visibleMonth), fontWeight = FontWeight.SemiBold)
                },
                actions = {
                    IconButton(onClick = { scope.launch { calendarState.animateScrollToMonth(visibleMonth.minusMonths(1)) } }) {
                        Icon(AppIcons.ChevronLeft, contentDescription = "Предыдущий месяц")
                    }
                    IconButton(onClick = { scope.launch { calendarState.animateScrollToMonth(visibleMonth.plusMonths(1)) } }) {
                        Icon(AppIcons.ChevronRight, contentDescription = "Следующий месяц")
                    }
                    IconButton(onClick = {
                        vm.select(today)
                        scope.launch { calendarState.animateScrollToMonth(currentMonth) }
                    }) { Icon(AppIcons.Today, contentDescription = "Сегодня") }
                    IconButton(onClick = onSearch) { Icon(AppIcons.Search, contentDescription = "Поиск") }
                    IconButton(onClick = { legend = true }) { Icon(AppIcons.Help, contentDescription = "Обозначения") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            state = rememberLazyListState(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            if (!state.loading && !state.hasSchedule) {
                item {
                    Banner(
                        "График работы не настроен. Задайте шаблон — календарь заполнится сменами автоматически.",
                        BannerKind.INFO,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        action = { TextButton(onClick = onSetupSchedule) { Text("Настроить") } },
                    )
                }
            }
            if (state.unconfirmedTotal > 0) {
                item {
                    Banner(
                        "Не отмечены часы: ${Formats.shifts(state.unconfirmedTotal)}",
                        BannerKind.WARNING,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        action = { TextButton(onClick = onOpenUnconfirmed) { Text("Отметить") } },
                    )
                }
            }
            if (state.settings.calendar.showMonthSummary && state.summary != null && state.month == visibleMonth) {
                item { MonthSummaryRow(state.summary!!, state.settings.pay.showForecast, onOpenFinance) }
            }
            item {
                // Cell height follows the width on phones and is limited on tablets and in landscape.
                val cellHeight = (LocalConfiguration.current.screenWidthDp.dp / 7 * 1.25f).coerceIn(56.dp, 96.dp)
                HorizontalCalendar(
                    state = calendarState,
                    modifier = Modifier.padding(horizontal = 6.dp),
                    dayContent = { day ->
                        DayCell(
                            day = day,
                            info = state.days[day.date],
                            selected = day.date == selected,
                            today = today,
                            showHolidays = state.settings.calendar.showHolidays,
                            height = cellHeight,
                            onClick = { vm.select(day.date) },
                            onLongClick = {
                                vm.select(day.date)
                                onNewShift(day.date, state.days[day.date]?.shifts?.any { it.isActive } == true)
                            },
                        )
                    },
                    monthHeader = { WeekHeader(daysOfWeek(state.settings.calendar.firstDayOfWeek)) },
                )
            }
            item { Spacer(Modifier.padding(top = 8.dp)) }
            item {
                DayPanel(
                    info = state.days[selected],
                    date = selected,
                    calc = state.calc,
                    now = state.now,
                    actions = actions,
                    onAddShift = { extra -> onNewShift(selected, extra) },
                    onAddAbsence = { type -> onNewAbsence(type, selected) },
                    onOpenAbsence = { onOpenAbsence(it.id) },
                    onSaveNote = { text -> scope.launchSafely(snackbar) { vm.saveNote(selected, text) } },
                )
            }
        }
    }
    if (legend) LegendDialog { legend = false }
}

@Composable
private fun MonthSummaryRow(summary: PeriodSummary, showForecast: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SummaryTile("Часы: факт / план", "${Formats.hoursDecimal(summary.workedMinutes)} / ${Formats.hoursDecimal(summary.plannedMinutes)}", onClick)
        SummaryTile("Подтверждено", money(summary.confirmedEarnings), onClick, AppTheme.status.positive)
        if (showForecast) {
            SummaryTile("Прогноз за месяц", summary.forecastEarnings?.let { money(it) } ?: "нет ставки", onClick)
        }
        SummaryTile("Смены", "${summary.workedShifts} из ${summary.scheduledShifts}", onClick)
    }
}

@Composable
private fun SummaryTile(label: String, value: String, onClick: () -> Unit, valueColor: Color = Color.Unspecified) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.Start) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = valueColor)
                Spacer(Modifier.width(2.dp))
            }
        }
    }
}
