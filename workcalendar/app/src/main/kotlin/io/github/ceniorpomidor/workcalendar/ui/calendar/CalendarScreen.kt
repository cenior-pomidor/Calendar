package io.github.ceniorpomidor.workcalendar.ui.calendar

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kizitonwose.calendar.compose.CalendarState
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

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

    // The state is created once: new inputs of rememberCalendarState would recreate it and reset the position.
    val firstDayOfWeek = state.settings.calendar.firstDayOfWeek
    val calendarState = rememberCalendarState(
        startMonth = currentMonth.minusMonths(120),
        endMonth = currentMonth.plusMonths(60),
        firstVisibleMonth = remember { initialDate?.let { YearMonth.from(it) } ?: currentMonth },
        firstDayOfWeek = remember { firstDayOfWeek },
        outDateStyle = OutDateStyle.EndOfGrid,
    )
    LaunchedEffect(firstDayOfWeek) {
        if (calendarState.firstDayOfWeek != firstDayOfWeek) calendarState.firstDayOfWeek = firstDayOfWeek
    }
    LaunchedEffect(initialDate) {
        if (initialDate != null) {
            vm.select(initialDate)
            calendarState.scrollToMonth(YearMonth.from(initialDate))
        }
    }
    // The month that takes most of the width: switches in the middle of a swipe, not when the
    // previous month has completely left the screen.
    val visibleMonth by remember(calendarState) { derivedStateOf { calendarState.mostVisibleMonth() } }
    LaunchedEffect(calendarState) {
        // A swipe must always end on a whole month, even if its animation was interrupted.
        snapshotFlow { calendarState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling && !calendarState.isAligned()) {
                try {
                    calendarState.animateScrollToMonth(calendarState.mostVisibleMonth())
                } catch (e: CancellationException) {
                    // The user touched the calendar again; keep watching unless the screen is gone.
                    ensureActive()
                }
            }
        }
    }
    LaunchedEffect(calendarState) {
        var previous: YearMonth? = null
        snapshotFlow { visibleMonth }.distinctUntilChanged().collectLatest { month ->
            // Data of neighbouring months is already loaded: reload only when the swipe has settled.
            snapshotFlow { calendarState.isScrollInProgress }.first { !it }
            vm.onMonthVisible(month)
            // The day panel follows the month shown by the calendar.
            if (previous != null && YearMonth.from(vm.selectedDate.value) != month) {
                vm.select(if (YearMonth.from(today) == month) today else month.atDay(1))
            }
            previous = month
        }
    }

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
        val showSummary = state.settings.calendar.showMonthSummary
        val summaryRow: @Composable () -> Unit = {
            MonthSummaryRow(state.summaries[visibleMonth], state.settings.pay.showForecast, onOpenFinance)
        }
        val calendar: @Composable (Dp) -> Unit = { cellHeight ->
            Column(Modifier.padding(horizontal = 6.dp)) {
                WeekHeader(daysOfWeek(firstDayOfWeek))
                HorizontalCalendar(
                    state = calendarState,
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
                )
            }
        }
        // Everything that can change its height lives below the calendar, in its own scrolling area:
        // the calendar never moves while months are swiped, and vertical scrolling cannot catch the swipe.
        val details: @Composable ColumnScope.() -> Unit = {
            if (!state.loading && !state.hasSchedule) {
                Banner(
                    "График работы не настроен. Задайте шаблон — календарь заполнится сменами автоматически.",
                    BannerKind.INFO,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    action = { TextButton(onClick = onSetupSchedule) { Text("Настроить") } },
                )
            }
            if (state.unconfirmedTotal > 0) {
                Banner(
                    "Не отмечены часы: ${Formats.shifts(state.unconfirmedTotal)}",
                    BannerKind.WARNING,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    action = { TextButton(onClick = onOpenUnconfirmed) { Text("Отметить") } },
                )
            }
            Spacer(Modifier.height(8.dp))
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
            Spacer(Modifier.height(16.dp))
        }

        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            if (maxWidth > maxHeight) {
                // Landscape and large screens: the calendar on the left, the day on the right.
                val calendarWidth = maxWidth * 0.55f
                val byWidth = (calendarWidth - 12.dp) / 7 * 1.25f
                val byHeight = (maxHeight - WEEK_HEADER_HEIGHT - 8.dp) / 6
                val cellHeight = minOf(byWidth, byHeight).coerceAtLeast(28.dp)
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.width(calendarWidth).padding(top = 4.dp)) { calendar(cellHeight) }
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                        if (showSummary) summaryRow()
                        details()
                    }
                }
            } else {
                val byWidth = (maxWidth - 12.dp) / 7 * 1.25f
                val summaryHeight = if (showSummary) SUMMARY_ROW_HEIGHT else 0.dp
                val byHeight = (maxHeight - summaryHeight - WEEK_HEADER_HEIGHT - MIN_PANEL_HEIGHT) / 6
                val cellHeight = minOf(byWidth, byHeight).coerceIn(40.dp, 96.dp)
                Column(Modifier.fillMaxSize()) {
                    if (showSummary) summaryRow()
                    calendar(cellHeight)
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) { details() }
                }
            }
        }
    }
    if (legend) LegendDialog { legend = false }
}

/** Approximate height of the summary row, used to size the calendar cells. */
private val SUMMARY_ROW_HEIGHT = 64.dp

/** Space kept for the day panel under the calendar in portrait. */
private val MIN_PANEL_HEIGHT = 150.dp

/** The month that occupies the largest part of the calendar viewport. */
private fun CalendarState.mostVisibleMonth(): YearMonth {
    val info = layoutInfo
    val viewportEnd = info.viewportEndOffset
    val best = info.visibleMonthsInfo.maxByOrNull { item ->
        minOf(item.offset + item.size, viewportEnd) - maxOf(item.offset, info.viewportStartOffset)
    }
    return best?.month?.yearMonth ?: firstVisibleMonth.yearMonth
}

/** True when the most visible month starts at the edge of the viewport (a whole month is shown). */
private fun CalendarState.isAligned(): Boolean {
    val target = mostVisibleMonth()
    val item = layoutInfo.visibleMonthsInfo.firstOrNull { it.month.yearMonth == target } ?: return true
    return abs(item.offset - layoutInfo.viewportStartOffset) <= 1
}

/** Totals of the month; a month that is not loaded yet keeps the same layout with dashes. */
@Composable
private fun MonthSummaryRow(summary: PeriodSummary?, showForecast: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val none = "—"
        SummaryTile(
            "Часы: факт / план",
            summary?.let { "${Formats.hoursDecimal(it.workedMinutes)} / ${Formats.hoursDecimal(it.plannedMinutes)}" } ?: none,
            onClick,
        )
        SummaryTile("Подтверждено", summary?.let { money(it.confirmedEarnings) } ?: none, onClick, AppTheme.status.positive)
        if (showForecast) {
            SummaryTile("Прогноз за месяц", summary?.let { s -> s.forecastEarnings?.let { money(it) } ?: "нет ставки" } ?: none, onClick)
        }
        SummaryTile("Смены", summary?.let { "${it.workedShifts} из ${it.scheduledShifts}" } ?: none, onClick)
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
