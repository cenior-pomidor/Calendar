package io.github.ceniorpomidor.workcalendar.domain.time

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Helpers for wall-clock times that respect daylight saving transitions. */
object TimeMath {
    const val MINUTES_PER_DAY: Int = 24 * 60

    /**
     * Real elapsed minutes between two wall-clock times in [zone]. Accounts for DST
     * transitions: a shift 20:00–08:00 over a "spring forward" night lasts 11 hours.
     */
    fun elapsedMinutes(start: LocalDateTime, end: LocalDateTime, zone: ZoneId): Long {
        if (!end.isAfter(start)) return 0
        return Duration.between(start.atZone(zone), end.atZone(zone)).toMinutes().coerceAtLeast(0)
    }

    /** Overlap of [aStart, aEnd) and [bStart, bEnd) in real minutes. */
    fun overlapMinutes(
        aStart: LocalDateTime,
        aEnd: LocalDateTime,
        bStart: LocalDateTime,
        bEnd: LocalDateTime,
        zone: ZoneId,
    ): Long {
        val s = if (aStart.isAfter(bStart)) aStart else bStart
        val e = if (aEnd.isBefore(bEnd)) aEnd else bEnd
        return elapsedMinutes(s, e, zone)
    }

    fun minuteOfDay(time: LocalTime): Int = time.hour * 60 + time.minute

    fun timeOfMinute(minuteOfDay: Int): LocalTime {
        val m = Math.floorMod(minuteOfDay, MINUTES_PER_DAY)
        return LocalTime.of(m / 60, m % 60)
    }

    fun atMinute(date: LocalDate, minuteOfDay: Int): LocalDateTime =
        date.atStartOfDay().plusMinutes(minuteOfDay.toLong())

    /**
     * Minutes of [start, end) that fall into the nightly window [nightStart, nightEnd)
     * (minutes of day, the window may wrap over midnight, e.g. 22:00–06:00).
     */
    fun nightMinutes(
        start: LocalDateTime,
        end: LocalDateTime,
        nightStartMinute: Int,
        nightEndMinute: Int,
        zone: ZoneId,
    ): Long {
        if (!end.isAfter(start) || nightStartMinute == nightEndMinute) return 0
        var total = 0L
        // Check windows starting from the day before the shift to cover early-morning parts.
        var day = start.toLocalDate().minusDays(1)
        val lastDay = end.toLocalDate()
        while (!day.isAfter(lastDay)) {
            val windowStart = atMinute(day, nightStartMinute)
            val windowEnd = if (nightEndMinute > nightStartMinute) {
                atMinute(day, nightEndMinute)
            } else {
                atMinute(day.plusDays(1), nightEndMinute)
            }
            total += overlapMinutes(start, end, windowStart, windowEnd, zone)
            day = day.plusDays(1)
        }
        return total
    }
}
