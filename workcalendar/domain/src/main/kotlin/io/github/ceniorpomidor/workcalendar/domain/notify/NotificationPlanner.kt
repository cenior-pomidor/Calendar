package io.github.ceniorpomidor.workcalendar.domain.notify

import io.github.ceniorpomidor.workcalendar.domain.model.Absence
import io.github.ceniorpomidor.workcalendar.domain.model.Money
import io.github.ceniorpomidor.workcalendar.domain.model.NotificationSettings
import io.github.ceniorpomidor.workcalendar.domain.model.Shift
import io.github.ceniorpomidor.workcalendar.domain.model.ShiftStatus
import io.github.ceniorpomidor.workcalendar.domain.pay.PayoutAmount
import io.github.ceniorpomidor.workcalendar.domain.pay.PayoutInstance
import io.github.ceniorpomidor.workcalendar.domain.time.TimeMath
import io.github.ceniorpomidor.workcalendar.domain.util.Formats
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

enum class NotificationKind {
    SHIFT_END,
    UNCONFIRMED,
    PAYOUT,
    VACATION_PAY,
    SICK_PAY,
}

/** A notification to show at [triggerAt]. The text is rebuilt from fresh data when it fires. */
data class PlannedNotification(
    val key: String,
    val kind: NotificationKind,
    val triggerAt: LocalDateTime,
    val title: String,
    val text: String,
    val shiftId: Long? = null,
    val absenceId: Long? = null,
    val payoutKey: String? = null,
    /** Amount for the quick "full shift" action, minutes. */
    val plannedMinutes: Long? = null,
)

/** Expected payment of vacation pay or a part of the sick benefit. */
data class AbsencePayment(
    val absence: Absence,
    val part: Part,
    val date: LocalDate,
    val amount: Money?,
) {
    enum class Part {
        VACATION,
        SICK_EMPLOYER,
        SICK_FUND,
        OTHER,
    }

    val key: String
        get() = when (part) {
            Part.VACATION -> "vacation:${absence.id}:${absence.startDate}"
            else -> "absence:${absence.id}:${part.name.lowercase()}:$date"
        }
}

/**
 * Decides which local notifications must be shown and when. Pure logic: the Android layer
 * schedules alarms for the returned items and re-runs the planner when an alarm fires to get
 * up-to-date texts (and to skip items that are no longer relevant).
 */
class NotificationPlanner(
    private val settings: NotificationSettings,
    private val zone: ZoneId,
    private val currency: String = "₽",
) {
    fun plan(
        now: LocalDateTime,
        until: LocalDateTime,
        shifts: List<Shift>,
        payouts: List<PayoutInstance>,
        absencePayments: List<AbsencePayment>,
        delivered: Set<String>,
    ): List<PlannedNotification> {
        val result = ArrayList<PlannedNotification>()
        if (settings.shiftEnd) result += shiftEnd(now, until, shifts, delivered)
        if (settings.unconfirmedReminder) result += unconfirmed(now, until, shifts, delivered)
        if (settings.payouts) result += payouts(now, until, payouts, delivered)
        result += absencePayments(now, until, absencePayments, delivered)
        return result.sortedBy { it.triggerAt }
    }

    /** Finds the current version of the notification with [key], or null if it is no longer needed. */
    fun find(
        key: String,
        now: LocalDateTime,
        shifts: List<Shift>,
        payouts: List<PayoutInstance>,
        absencePayments: List<AbsencePayment>,
    ): PlannedNotification? {
        val windowEnd = now.plusDays(1)
        val all = plan(now, windowEnd, shifts, payouts, absencePayments, emptySet())
        return all.firstOrNull { it.key == key && !it.triggerAt.isAfter(now.plusMinutes(5)) }
    }

    fun unconfirmedCount(shifts: List<Shift>, at: LocalDateTime): Int =
        shifts.count { it.status == ShiftStatus.PLANNED && !at.isBefore(it.plannedEnd.plusMinutes(settings.shiftEndDelayMinutes.toLong())) }

    private fun shiftEnd(now: LocalDateTime, until: LocalDateTime, shifts: List<Shift>, delivered: Set<String>): List<PlannedNotification> {
        val lookback = now.minusHours(SHIFT_END_LOOKBACK_HOURS)
        return shifts.filter { it.status == ShiftStatus.PLANNED }.mapNotNull { shift ->
            val trigger = shift.plannedEnd.plusMinutes(settings.shiftEndDelayMinutes.toLong())
            val key = shiftKey(shift)
            if (key in delivered || trigger.isAfter(until) || trigger.isBefore(lookback)) return@mapNotNull null
            val minutes = shift.plannedPaidMinutes(zone)
            PlannedNotification(
                key = key,
                kind = NotificationKind.SHIFT_END,
                triggerAt = if (trigger.isBefore(now)) now else trigger,
                title = "Смена завершена — отметьте часы",
                text = "${Formats.weekdayShort(shift.date.dayOfWeek)}, ${Formats.shortDate(shift.date)}: " +
                    "${Formats.timeRange(shift.plannedStart, shift.plannedEnd)}, по плану ${Formats.hours(minutes)}",
                shiftId = shift.id,
                plannedMinutes = minutes,
            )
        }
    }

    private fun unconfirmed(now: LocalDateTime, until: LocalDateTime, shifts: List<Shift>, delivered: Set<String>): List<PlannedNotification> {
        val result = ArrayList<PlannedNotification>()
        var day = now.toLocalDate()
        while (!day.isAfter(until.toLocalDate())) {
            val trigger = TimeMath.atMinute(day, settings.unconfirmedReminderMinute)
            val key = "unconfirmed:$day"
            if (!trigger.isBefore(now) && !trigger.isAfter(until) && key !in delivered) {
                // Shifts that ended at least an hour before the reminder (fresh ones get their own notification).
                val count = shifts.count {
                    it.status == ShiftStatus.PLANNED &&
                        !trigger.isBefore(it.plannedEnd.plusMinutes(settings.shiftEndDelayMinutes.toLong() + 60))
                }
                if (count > 0) {
                    result += PlannedNotification(
                        key = key,
                        kind = NotificationKind.UNCONFIRMED,
                        triggerAt = trigger,
                        title = "Не отмечены отработанные часы",
                        text = "${Formats.shifts(count)} без подтверждённых часов. Заработок по ним не учитывается, пока часы не внесены.",
                    )
                }
            }
            day = day.plusDays(1)
        }
        return result
    }

    private fun payouts(now: LocalDateTime, until: LocalDateTime, payouts: List<PayoutInstance>, delivered: Set<String>): List<PlannedNotification> =
        payouts.mapNotNull { payout ->
            if (!payout.rule.notify || payout.isReceived || payout.key in delivered) return@mapNotNull null
            val trigger = TimeMath.atMinute(payout.payDate.minusDays(payout.rule.notifyDaysBefore.toLong()), settings.payoutMinute)
            if (trigger.isAfter(until)) return@mapNotNull null
            // Deliver late (e.g. the phone was off) while the payday has not passed.
            if (trigger.isBefore(now) && payout.payDate.isBefore(now.toLocalDate())) return@mapNotNull null
            val whenText = when (val days = java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(), payout.payDate)) {
                0L -> "сегодня"
                1L -> "завтра"
                else -> "через ${Formats.days(days.toInt())}"
            }
            PlannedNotification(
                key = payout.key,
                kind = NotificationKind.PAYOUT,
                triggerAt = if (trigger.isBefore(now)) now else trigger,
                title = "${payout.rule.name} $whenText, ${Formats.shortDate(payout.payDate)}",
                text = payoutText(payout),
                payoutKey = payout.key,
            )
        }

    fun payoutText(payout: PayoutInstance): String {
        val period = "за ${Formats.period(payout.period.start, payout.period.endInclusive)}"
        return when (val amount = payout.amount) {
            is PayoutAmount.Calculated -> {
                val prefix = if (amount.isEstimate) "≈ " else ""
                val note = if (amount.isEstimate) " (оценка: есть неподтверждённые смены)" else ""
                "Расчётная сумма: $prefix${Formats.money(amount.net, currency)} $period$note"
            }
            is PayoutAmount.Insufficient -> "Сумму рассчитать нельзя: ${amount.reason}"
        }
    }

    private fun absencePayments(now: LocalDateTime, until: LocalDateTime, payments: List<AbsencePayment>, delivered: Set<String>): List<PlannedNotification> =
        payments.mapNotNull { payment ->
            val enabled = when (payment.part) {
                AbsencePayment.Part.VACATION -> settings.vacationPay
                AbsencePayment.Part.SICK_EMPLOYER, AbsencePayment.Part.SICK_FUND -> settings.sickPay
                AbsencePayment.Part.OTHER -> settings.vacationPay
            }
            if (!enabled || payment.key in delivered) return@mapNotNull null
            val trigger = TimeMath.atMinute(payment.date, settings.absenceMinute)
            if (trigger.isAfter(until)) return@mapNotNull null
            val lateAllowed = when (payment.part) {
                AbsencePayment.Part.VACATION -> payment.absence.startDate.isAfter(now.toLocalDate())
                else -> !payment.date.isBefore(now.toLocalDate())
            }
            if (trigger.isBefore(now) && !lateAllowed) return@mapNotNull null
            val amountText = payment.amount?.let { Formats.money(it, currency) } ?: "сумма не рассчитана"
            val absence = payment.absence
            val period = Formats.period(absence.startDate, absence.endDate)
            val (title, text) = when (payment.part) {
                AbsencePayment.Part.VACATION ->
                    "Скоро отпускные: $amountText" to "Отпуск $period (${Formats.days(absence.calendarDays)}). Отпускные обычно выплачивают не позднее чем за 3 дня до начала отпуска."
                AbsencePayment.Part.SICK_EMPLOYER ->
                    "Сегодня выплата больничного: $amountText" to "Часть пособия от работодателя за больничный $period"
                AbsencePayment.Part.SICK_FUND ->
                    "Ожидается выплата пособия: $amountText" to "Часть пособия от Соцфонда за больничный $period"
                AbsencePayment.Part.OTHER ->
                    "Выплата: $amountText" to "${absence.displayTitle()} $period"
            }
            PlannedNotification(
                key = payment.key,
                kind = if (payment.part == AbsencePayment.Part.VACATION) NotificationKind.VACATION_PAY else NotificationKind.SICK_PAY,
                triggerAt = if (trigger.isBefore(now)) now else trigger,
                title = title,
                text = text,
                absenceId = absence.id,
            )
        }

    companion object {
        const val SHIFT_END_LOOKBACK_HOURS: Long = 24

        fun shiftKey(shift: Shift): String = "shift:${shift.id}:${shift.plannedEnd}"
    }
}
