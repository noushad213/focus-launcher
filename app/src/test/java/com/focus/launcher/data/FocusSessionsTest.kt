package com.focus.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FocusSessionsTest {
    @Test fun sandboxAllowsOnlyChosenAppsAndRecoveryPaths() {
        val chosen = setOf("notes", "music", "reader")
        for (pkg in chosen + setOf("com.android.systemui", "com.android.settings", "dialer")) {
            assertTrue(isFocusPackageAllowed(pkg, chosen, "dialer"))
        }
        assertFalse(isFocusPackageAllowed("browser", chosen, "dialer"))
        assertFalse(isFocusPackageAllowed("other.launcher", chosen, "dialer"))
        assertFalse(isFocusPackageAllowed("focus", chosen, "dialer"))
    }

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
