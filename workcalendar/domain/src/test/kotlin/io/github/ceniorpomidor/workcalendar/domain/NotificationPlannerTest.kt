package io.github.ceniorpomidor.workcalendar.domain

import io.github.ceniorpomidor.workcalendar.domain.TestData.date
import io.github.ceniorpomidor.workcalendar.domain.TestData.dateTime
import io.github.ceniorpomidor.workcalendar.domain.TestData.shift
import io.github.ceniorpomidor.workcalendar.domain.model.Absence
import io.github.ceniorpomidor.workcalendar.domain.model.AbsenceType
import io.github.ceniorpomidor.workcalendar.domain.model.Money
import io.github.ceniorpomidor.workcalendar.domain.model.NotificationSettings
import io.github.ceniorpomidor.workcalendar.domain.model.PayoutRule
import io.github.ceniorpomidor.workcalendar.domain.model.ShiftStatus
import io.github.ceniorpomidor.workcalendar.domain.notify.AbsencePayment
import io.github.ceniorpomidor.workcalendar.domain.notify.NotificationKind
import io.github.ceniorpomidor.workcalendar.domain.notify.NotificationPlanner
import io.github.ceniorpomidor.workcalendar.domain.pay.PayoutAmount
import io.github.ceniorpomidor.workcalendar.domain.pay.PayoutInstance
import io.github.ceniorpomidor.workcalendar.domain.time.DateRange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.YearMonth

class NotificationPlannerTest {
    private val planner = NotificationPlanner(NotificationSettings(), TestData.zone)

    private fun payout(): PayoutInstance {
        val rule = PayoutRule.defaultAdvance().copy(id = 1)
        return PayoutInstance(
            rule = rule,
            periodMonth = YearMonth.of(2026, 10),
            period = DateRange(date("2026-10-01"), date("2026-10-15")),
            payDate = date("2026-10-23"),
            nominalPayDate = date("2026-10-25"),
            amount = PayoutAmount.Calculated(Money.ofRubles(26_400), Money.ZERO, Money.ofRubles(26_400), false, 0, 0, emptyList()),
            payment = null,
        )
    }

    @Test
    fun `scenario 5 - shift end reminder is planned at the end of the shift`() {
        val now = dateTime("2026-10-05T08:00")
        val s = shift(10, "2026-10-05")
        val plan = planner.plan(now, now.plusDays(2), listOf(s), emptyList(), emptyList(), emptySet())
        val n = plan.first { it.kind == NotificationKind.SHIFT_END }
        assertEquals(dateTime("2026-10-05T18:00"), n.triggerAt)
        assertEquals(10L, n.shiftId)
        assertEquals(480L, n.plannedMinutes)
    }

    @Test
    fun `no reminder after confirmation or delivery`() {
        val now = dateTime("2026-10-05T08:00")
        val confirmed = shift(10, "2026-10-05", status = ShiftStatus.CONFIRMED, worked = 480)
        assertTrue(planner.plan(now, now.plusDays(1), listOf(confirmed), emptyList(), emptyList(), emptySet()).none { it.kind == NotificationKind.SHIFT_END })
        val planned = shift(10, "2026-10-05")
        val delivered = setOf(NotificationPlanner.shiftKey(planned))
        assertTrue(planner.plan(now, now.plusDays(1), listOf(planned), emptyList(), emptyList(), delivered).none { it.kind == NotificationKind.SHIFT_END })
    }

    @Test
    fun `scenario 6 - ignored reminder leaves the shift unconfirmed and a daily reminder follows`() {
        val now = dateTime("2026-10-05T19:00")
        val s = shift(10, "2026-10-05")
        val plan = planner.plan(now, now.plusDays(1), listOf(s), emptyList(), emptyList(), setOf(NotificationPlanner.shiftKey(s)))
        val daily = plan.single { it.kind == NotificationKind.UNCONFIRMED }
        assertEquals(dateTime("2026-10-05T20:00"), daily.triggerAt)
        assertEquals(1, planner.unconfirmedCount(listOf(s), now))
        // A snoozed reminder fires later with the current count.
        val snoozed = planner.find(daily.key, dateTime("2026-10-05T21:00"), listOf(s), emptyList(), emptyList())
        assertEquals(NotificationKind.UNCONFIRMED, snoozed?.kind)
        assertNull(planner.find(daily.key, dateTime("2026-10-05T21:00"), listOf(s.copy(status = ShiftStatus.CONFIRMED)), emptyList(), emptyList()))
    }

    @Test
    fun `missed shift reminder is delivered late within a day`() {
        val now = dateTime("2026-10-06T07:00")
        val s = shift(10, "2026-10-05")
        val n = planner.plan(now, now.plusDays(1), listOf(s), emptyList(), emptyList(), emptySet()).first { it.kind == NotificationKind.SHIFT_END }
        assertEquals(now, n.triggerAt)
    }

    @Test
    fun `payout reminder the day before`() {
        val now = dateTime("2026-10-20T12:00")
        val n = planner.plan(now, now.plusDays(10), emptyList(), listOf(payout()), emptyList(), emptySet()).single()
        assertEquals(dateTime("2026-10-22T10:00"), n.triggerAt)
        assertTrue(n.text.contains("26"))
        val received = payout().copy(
            payment = io.github.ceniorpomidor.workcalendar.domain.model.Payment(
                type = io.github.ceniorpomidor.workcalendar.domain.model.PaymentType.ADVANCE,
                amount = Money.ofRubles(1),
                date = date("2026-10-21"),
            ),
        )
        assertTrue(planner.plan(now, now.plusDays(10), emptyList(), listOf(received), emptyList(), emptySet()).isEmpty())
    }

    @Test
    fun `vacation and sick pay reminders`() {
        val now = dateTime("2026-11-01T12:00")
        val vacation = Absence(id = 3, type = AbsenceType.VACATION, startDate = date("2026-11-09"), endDate = date("2026-11-22"), manualAmount = Money.ofRubles(40_000))
        val payment = AbsencePayment(vacation, AbsencePayment.Part.VACATION, date("2026-11-05"), vacation.amount)
        val plan = planner.plan(now, now.plusDays(10), emptyList(), emptyList(), listOf(payment), emptySet())
        val n = plan.single()
        assertEquals(NotificationKind.VACATION_PAY, n.kind)
        assertEquals(dateTime("2026-11-05T09:00"), n.triggerAt)
        assertTrue(n.title.contains("40"))
        assertNotNull(planner.find(n.key, dateTime("2026-11-05T09:00"), emptyList(), emptyList(), listOf(payment)))
        assertNull(planner.find(n.key, dateTime("2026-11-10T09:00"), emptyList(), emptyList(), listOf(payment)))
    }
}
