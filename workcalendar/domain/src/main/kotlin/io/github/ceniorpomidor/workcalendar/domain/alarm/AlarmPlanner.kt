package io.github.ceniorpomidor.workcalendar.domain.alarm

import io.github.ceniorpomidor.workcalendar.domain.model.AlarmSettings
import io.github.ceniorpomidor.workcalendar.domain.model.AlarmTimeMode
import io.github.ceniorpomidor.workcalendar.domain.model.Shift
import io.github.ceniorpomidor.workcalendar.domain.model.ShiftKind
import io.github.ceniorpomidor.workcalendar.domain.model.ShiftStatus
import io.github.ceniorpomidor.workcalendar.domain.time.TimeMath
import io.github.ceniorpomidor.workcalendar.domain.util.Formats
import java.time.LocalDate
import java.time.LocalDateTime

/** An alarm that is going to ring. */
data class PlannedAlarm(
    val at: LocalDateTime,
    /** Day the alarm belongs to: the date of the shift or the day chosen by the user. */
    val date: LocalDate,
    /** First shift of the day, if any. */
    val shift: Shift?,
    /** Set or changed by the user for this day only. */
    val single: Boolean,
) {
    /** Identifies the alarm, e.g. to remember that it has already rung. */
    val key: String get() = "$date@$at"

    /** Text under the time: what the alarm is for. */
    fun description(): String {
        val shiftText = shift?.let { "смена ${Formats.timeRange(it.plannedStart, it.plannedEnd)}" }
        return when {
            single && shiftText != null -> "Только в этот день · $shiftText"
            single -> "Только в этот день"
            shiftText != null -> shiftText.replaceFirstChar { it.uppercase() }
            else -> "Будильник"
        }
    }
}

/** Plans wake-up alarms: before every working day and on single days chosen by the user. */
object AlarmPlanner {
    /** How many days ahead the next alarm is looked for (covers vacations). */
    const val LOOKAHEAD_DAYS = 62

    /** First planned shift of the day that the working day alarm rings for. */
    fun workShift(settings: AlarmSettings, date: LocalDate, shifts: List<Shift>): Shift? = shifts
        .filter { it.date == date && it.status == ShiftStatus.PLANNED && (settings.includeExtraShifts || it.kind == ShiftKind.REGULAR) }
        .minByOrNull { it.plannedStart }

    /** Working day alarm of [date], without the changes made for single days. */
    fun regular(settings: AlarmSettings, date: LocalDate, shifts: List<Shift>): PlannedAlarm? {
        if (!settings.workDays) return null
        val shift = workShift(settings, date, shifts) ?: return null
        val at = when (settings.mode) {
            AlarmTimeMode.BEFORE_SHIFT -> shift.plannedStart.minusMinutes(settings.minutesBefore.toLong())
            AlarmTimeMode.FIXED_TIME -> date.atTime(TimeMath.timeOfMinute(settings.fixedMinute))
        }
        return PlannedAlarm(at, date, shift, single = false)
    }

    /** Alarm of [date] including the user's change for this day. */
    fun forDay(settings: AlarmSettings, date: LocalDate, shifts: List<Shift>): PlannedAlarm? {
        val day = settings.dayAlarm(date) ?: return regular(settings, date, shifts)
        val minute = day.minute ?: return null
        val shift = workShift(settings.copy(includeExtraShifts = true), date, shifts)
        return PlannedAlarm(date.atTime(TimeMath.timeOfMinute(minute)), date, shift, single = true)
    }

    /**
     * Alarms after [now] for the next [days] days, sorted by time. [shifts] must contain the
     * shifts from today to today + [days] + 1: an alarm before an early shift can ring the
     * evening before. [skipKey] is the alarm that has just rung.
     */
    fun upcoming(settings: AlarmSettings, shifts: List<Shift>, now: LocalDateTime, days: Int = LOOKAHEAD_DAYS, skipKey: String? = null): List<PlannedAlarm> {
        val today = now.toLocalDate()
        val result = ArrayList<PlannedAlarm>()
        for (i in 0..days + 1) {
            val alarm = forDay(settings, today.plusDays(i.toLong()), shifts) ?: continue
            if (alarm.at.isAfter(now) && alarm.key != skipKey) result += alarm
        }
        return result.sortedBy { it.at }
    }
}
