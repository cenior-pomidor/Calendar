package io.github.ceniorpomidor.workcalendar.domain

import io.github.ceniorpomidor.workcalendar.domain.TestData.date
import io.github.ceniorpomidor.workcalendar.domain.TestData.dateTime
import io.github.ceniorpomidor.workcalendar.domain.TestData.shift
import io.github.ceniorpomidor.workcalendar.domain.alarm.AlarmPlanner
import io.github.ceniorpomidor.workcalendar.domain.model.AlarmSettings
import io.github.ceniorpomidor.workcalendar.domain.model.AlarmTimeMode
import io.github.ceniorpomidor.workcalendar.domain.model.ShiftKind
import io.github.ceniorpomidor.workcalendar.domain.model.ShiftOrigin
import io.github.ceniorpomidor.workcalendar.domain.model.ShiftStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AlarmPlannerTest {
    private val workDays = AlarmSettings(workDays = true, minutesBefore = 90)

    // Mon 05.10 and Tue 06.10 08:00–20:00, Wed 07.10 off, Thu 08.10 extra shift 10:00–18:00.
    private val shifts = listOf(
        shift(1, "2026-10-05", start = "08:00", end = "20:00"),
        shift(2, "2026-10-06", start = "08:00", end = "20:00"),
        shift(3, "2026-10-08", start = "10:00", end = "18:00", kind = ShiftKind.EXTRA, origin = ShiftOrigin.MANUAL),
    )

    @Test
    fun `alarm before every working day`() {
        val alarms = AlarmPlanner.upcoming(workDays, shifts, dateTime("2026-10-05T05:00"), days = 7)
        assertEquals(
            listOf(dateTime("2026-10-05T06:30"), dateTime("2026-10-06T06:30"), dateTime("2026-10-08T08:30")),
            alarms.map { it.at },
        )
        assertEquals(1L, alarms.first().shift?.id)
        assertFalse(alarms.first().single)
        assertEquals("Смена 08:00–20:00", alarms.first().description())
    }

    @Test
    fun `fixed time and days without extra shifts`() {
        val settings = workDays.copy(mode = AlarmTimeMode.FIXED_TIME, fixedMinute = 7 * 60, includeExtraShifts = false)
        val alarms = AlarmPlanner.upcoming(settings, shifts, dateTime("2026-10-05T05:00"), days = 7)
        assertEquals(listOf(dateTime("2026-10-05T07:00"), dateTime("2026-10-06T07:00")), alarms.map { it.at })
    }

    @Test
    fun `past alarms, the alarm that has rung and the switched off setting are skipped`() {
        val now = dateTime("2026-10-05T06:30")
        val first = AlarmPlanner.upcoming(workDays, shifts, now.minusMinutes(1), days = 7).first()
        assertEquals(dateTime("2026-10-06T06:30"), AlarmPlanner.upcoming(workDays, shifts, now, days = 7).first().at)
        assertEquals(
            dateTime("2026-10-06T06:30"),
            AlarmPlanner.upcoming(workDays, shifts, now.minusSeconds(30), days = 7, skipKey = first.key).first().at,
        )
        assertTrue(AlarmPlanner.upcoming(AlarmSettings(workDays = false), shifts, now, days = 7).isEmpty())
    }

    @Test
    fun `no alarm for cancelled, moved, covered and confirmed shifts`() {
        val list = listOf(
            shift(1, "2026-10-05", status = ShiftStatus.CANCELLED),
            shift(2, "2026-10-06", status = ShiftStatus.MOVED),
            shift(3, "2026-10-07", status = ShiftStatus.COVERED),
            shift(4, "2026-10-08", status = ShiftStatus.CONFIRMED, worked = 480),
        )
        assertTrue(AlarmPlanner.upcoming(workDays, list, dateTime("2026-10-04T12:00"), days = 7).isEmpty())
    }

    @Test
    fun `alarm of an early shift rings the evening before`() {
        val night = listOf(shift(1, "2026-10-06", start = "00:30", end = "08:30", breakMinutes = 0))
        val alarms = AlarmPlanner.upcoming(workDays, night, dateTime("2026-10-05T12:00"), days = 0)
        assertEquals(listOf(dateTime("2026-10-05T23:00")), alarms.map { it.at })
        assertEquals(date("2026-10-06"), alarms.single().date)
    }

    @Test
    fun `single days replace or switch off the working day alarm`() {
        val today = date("2026-10-05")
        val settings = workDays
            .withDay(date("2026-10-06"), null, today)
            .withDay(date("2026-10-05"), 5 * 60 + 45, today)
            .withDay(date("2026-10-07"), 9 * 60, today)
        val alarms = AlarmPlanner.upcoming(settings, shifts, dateTime("2026-10-05T05:00"), days = 7)
        assertEquals(listOf(dateTime("2026-10-05T05:45"), dateTime("2026-10-07T09:00"), dateTime("2026-10-08T08:30")), alarms.map { it.at })
        assertTrue(alarms[0].single)
        assertEquals("Только в этот день · смена 08:00–20:00", alarms[0].description())
        assertEquals("Только в этот день", alarms[1].description())
        assertNull(AlarmPlanner.forDay(settings, date("2026-10-06"), shifts))
        assertEquals(dateTime("2026-10-06T06:30"), AlarmPlanner.regular(settings, date("2026-10-06"), shifts)?.at)

        // Single days work while the working day alarm is off; old days are dropped.
        val off = settings.copy(workDays = false).withoutDay(date("2026-10-05"), date("2026-10-07"))
        assertEquals(listOf(date("2026-10-06"), date("2026-10-07")), off.days.map { it.date })
        assertEquals(listOf(dateTime("2026-10-07T09:00")), AlarmPlanner.upcoming(off, shifts, dateTime("2026-10-06T10:00"), days = 7).map { it.at })
    }
}
