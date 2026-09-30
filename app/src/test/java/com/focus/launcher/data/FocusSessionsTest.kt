package com.focus.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FocusSessionsTest {
    @Test fun splitsTimeAtLocalMidnight() {
        val zone = ZoneId.of("Asia/Kolkata")
        val first = LocalDate.of(2026, 9, 29)
        val start = first.atTime(23, 50).atZone(zone).toInstant().toEpochMilli()
        val end = first.plusDays(1).atTime(0, 20).atZone(zone).toInstant().toEpochMilli()

        assertEquals(
            mapOf(first to 10 * 60_000L, first.plusDays(1) to 20 * 60_000L),
            focusMinutesByDay(listOf(FocusSession(start, end)), zone),
        )
    }
}
