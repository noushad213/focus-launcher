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

enum class FocusMode { STOPWATCH, SANDBOX }

/** Local session history shared by the open-ended timer and the 25-minute sandbox. */
class FocusSessions(context: Context) {
    private val prefs = context.getSharedPreferences("focus_sessions", Context.MODE_PRIVATE)

    val activeStart: Long? get() = prefs.getLong("active_start", 0L).takeIf { it > 0L }
    val activeEnd: Long? get() = prefs.getLong("active_end", 0L).takeIf { it > 0L }
    val activeMode: FocusMode? get() = if (activeStart == null) null else
        if (prefs.getString("active_mode", null) == FocusMode.SANDBOX.name || activeEnd != null) FocusMode.SANDBOX else FocusMode.STOPWATCH
    val selectedPackages: Set<String> get() = prefs.getStringSet("selected_packages", emptySet())?.toSet() ?: emptySet()
    val allowedPackages: Set<String> get() = prefs.getStringSet("active_packages", emptySet())?.toSet() ?: emptySet()

    fun choose(packages: Set<String>) {
        require(packages.size <= MAX_APPS)
        prefs.edit { putStringSet("selected_packages", packages) }
    }

    fun start(mode: FocusMode, now: Long = System.currentTimeMillis()) {
        if (activeStart == null) prefs.edit {
            putLong("active_start", now)
            putString("active_mode", mode.name)
            if (mode == FocusMode.SANDBOX) {
                putLong("active_end", now + DURATION_MS)
                putStringSet("active_packages", selectedPackages)
            } else {
                remove("active_end")
                remove("active_packages")
            }
        }
    }

    fun isActive(now: Long = System.currentTimeMillis()): Boolean =
        activeStart != null && (activeMode == FocusMode.STOPWATCH || activeEnd?.let { now < it } == true)

    fun isSandboxActive(now: Long = System.currentTimeMillis()): Boolean =
        activeMode == FocusMode.SANDBOX && isActive(now)

    /** Finalize an expired session on the next UI tick or foreground change. */
    fun finishIfExpired(now: Long = System.currentTimeMillis()): Boolean {
        if (activeMode != FocusMode.SANDBOX) return false
        val end = activeEnd ?: return false
        if (now < end) return false
        stop(end)
        return true
    }

    @Synchronized
    fun stop(now: Long = System.currentTimeMillis()): FocusSession? {
        val start = activeStart ?: return null
        val session = FocusSession(start, now.coerceAtLeast(start).coerceAtMost(activeEnd ?: now))
        val saved = try { JSONArray(prefs.getString("history", "[]")) } catch (_: Exception) { JSONArray() }
        if (session.durationMs > 0) saved.put(JSONObject().put("start", start).put("end", session.endMs))
        prefs.edit { putString("history", saved.toString()); remove("active_start"); remove("active_end"); remove("active_mode"); remove("active_packages") }
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

    companion object {
        const val MAX_APPS = 3
        const val DURATION_MS = 25 * 60_000L
    }
}

/** System UI and recovery paths remain reachable during a voluntary focus session. */
fun isFocusPackageAllowed(pkg: String, selected: Set<String>, dialer: String?): Boolean =
    pkg in selected || pkg == "com.android.systemui" ||
        pkg == "com.android.settings" || pkg == dialer

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
