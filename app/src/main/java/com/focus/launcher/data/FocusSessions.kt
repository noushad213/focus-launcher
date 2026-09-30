package com.focus.launcher.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class FocusSession(val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0)
}

/** Local session history; the active start is committed before the stopwatch appears. */
class FocusSessions(context: Context) {
    private val prefs = context.getSharedPreferences("focus_sessions", Context.MODE_PRIVATE)

    val activeStart: Long? get() = prefs.getLong("active_start", 0L).takeIf { it > 0L }

    fun start(now: Long = System.currentTimeMillis()) {
        if (activeStart == null) prefs.edit { putLong("active_start", now) }
    }

    @Synchronized
    fun stop(now: Long = System.currentTimeMillis()): FocusSession? {
        val start = activeStart ?: return null
        val session = FocusSession(start, now.coerceAtLeast(start))
        val saved = try { JSONArray(prefs.getString("history", "[]")) } catch (_: Exception) { JSONArray() }
        if (session.durationMs > 0) saved.put(JSONObject().put("start", start).put("end", session.endMs))
        prefs.edit { putString("history", saved.toString()); remove("active_start") }
        return session
    }

    fun history(): List<FocusSession> {
        val values = try { JSONArray(prefs.getString("history", "[]")) } catch (_: Exception) { JSONArray() }
        return (0 until values.length()).mapNotNull { index ->
            val item = values.optJSONObject(index) ?: return@mapNotNull null
            val start = item.optLong("start")
            val end = item.optLong("end")
            if (start > 0 && end >= start) FocusSession(start, end) else null
        }
    }
}

/** Split a session at local midnight so weekly and monthly charts agree with the calendar. */
fun focusMinutesByDay(sessions: List<FocusSession>, zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, Long> {
    val totals = mutableMapOf<LocalDate, Long>()
    sessions.forEach { session ->
        var cursor = session.startMs
        while (cursor < session.endMs) {
            val date = Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate()
            val nextMidnight = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val end = minOf(session.endMs, nextMidnight)
            totals[date] = (totals[date] ?: 0L) + end - cursor
            cursor = end
        }
    }
    return totals
}
