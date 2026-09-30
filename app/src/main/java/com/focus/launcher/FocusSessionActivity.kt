package com.focus.launcher

import android.content.Context
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focus.launcher.data.FocusSessions
import com.focus.launcher.data.focusMinutesByDay
import com.focus.launcher.ui.components.ConfirmDialog
import com.focus.launcher.ui.components.FocusButton
import com.focus.launcher.ui.components.Hairline
import com.focus.launcher.ui.components.T
import com.focus.launcher.ui.theme.FocusTheme
import com.focus.launcher.ui.theme.LocalFocusColors
import com.focus.launcher.ui.theme.applyFocusWindow
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.temporal.WeekFields

class FocusSessionActivity : ComponentActivity() {
    private val sessions by lazy { FocusSessions(this) }
    private var active by mutableStateOf(false)
    private var confirming by mutableStateOf(false)
    private var exitAfterStop by mutableStateOf(false)
    private var revision by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        active = sessions.activeStart != null
        setContent {
            val settings by Graph.settings.flow.collectAsStateWithLifecycle()
            LaunchedEffect(settings.dark) { applyFocusWindow(settings.dark) }
            FocusTheme(settings) {
                FocusSessionScreen(active, sessions, revision, ::start, ::stop, ::askToStop, ::askToLeave, ::finish, confirming, exitAfterStop) {
                    confirming = false
                }
            }
        }
    }

    private fun start() { sessions.start(); active = true }
    private fun stop() { sessions.stop(); active = false; revision++ }
    private fun askToStop() { exitAfterStop = false; confirming = true }
    private fun askToLeave() { if (active) { exitAfterStop = true; confirming = true } else finish() }

    @Deprecated("Use BackHandler for Compose navigation")
    override fun onBackPressed() = askToLeave()

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Pulling down notifications removes window focus without necessarily stopping the activity.
        // The confirmation dialog has its own window, so it is excluded here.
        val interactive = (getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
        if (!hasFocus && active && !confirming && interactive) stop()
    }

    override fun onStop() {
        super.onStop()
        // Locking the screen counts as time away; switching to another screen ends the session.
        val interactive = (getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
        if (active && interactive && !confirming) stop()
    }
}

@Composable
private fun FocusSessionScreen(
    active: Boolean,
    sessions: FocusSessions,
    revision: Int,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onStopRequest: () -> Unit,
    onBack: () -> Unit,
    finish: () -> Unit,
    confirming: Boolean,
    exitAfterStop: Boolean,
    cancelConfirm: () -> Unit,
) {
    val colors = LocalFocusColors.current
    var tab by remember { mutableStateOf(0) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active) {
        while (active) { now = System.currentTimeMillis(); delay(250) }
    }
    BackHandler { onBack() }
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            T("←", Modifier.clickable(onClick = onBack).padding(end = 24.dp), size = 24.sp)
            T("Focus", Modifier.weight(1f), size = 22.sp, weight = FontWeight.Medium)
            if (!active) T("Analysis", Modifier.clickable { tab = 1 }.padding(8.dp), size = 14.sp)
        }
        Hairline()
        if (active) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    T("TIME FOR YOURSELF", size = 11.sp, color = colors.dim)
                    Spacer(Modifier.height(24.dp))
                    val elapsed = ((now - (sessions.activeStart ?: now)).coerceAtLeast(0) / 1000)
                    T("%02d:%02d:%02d".format(elapsed / 3600, elapsed / 60 % 60, elapsed % 60), size = 52.sp, weight = FontWeight.Medium)
                    Spacer(Modifier.height(18.dp))
                    T("One moment at a time ✦", color = colors.dim)
                }
            }
            FocusButton("Stop & save", Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp), onClick = onStopRequest)
        } else if (tab == 0) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    T("Take a little time away", size = 24.sp, weight = FontWeight.Medium, align = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    T("Your time starts when you tap Focus.", color = colors.dim, align = TextAlign.Center)
                    Spacer(Modifier.height(32.dp))
                    FocusButton("Focus", primary = true, onClick = onStart)
                }
            }
            T("Your sessions are saved in Analysis", Modifier.fillMaxWidth().padding(bottom = 28.dp), size = 13.sp, color = colors.dim, align = TextAlign.Center)
        } else {
            FocusAnalysis(sessions, revision, Modifier.weight(1f))
            FocusButton("Start focus", Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp), primary = true, onClick = { tab = 0; onStart() })
        }
    }
    if (confirming) ConfirmDialog("End focus?", "Your focused time will be saved to Analysis.", if (exitAfterStop) "Stop & leave" else "Stop & save", cancelConfirm) {
        onStop()
        if (exitAfterStop) finish() else tab = 1
    }
}

@Composable
private fun FocusAnalysis(sessions: FocusSessions, revision: Int, modifier: Modifier = Modifier) {
    val history = remember(revision) { sessions.history() }
    val totals = remember(history) { focusMinutesByDay(history) }
    val today = LocalDate.now()
    val weekStart = today.with(WeekFields.of(java.util.Locale.getDefault()).firstDayOfWeek)
    val days = (0..6).map { weekStart.plusDays(it.toLong()) }
    val monthDays = (0 until today.lengthOfMonth()).map { today.withDayOfMonth(it + 1) }
    val todayMs = totals[today] ?: 0L
    val weekMs = days.sumOf { totals[it] ?: 0L }
    val monthMs = monthDays.sumOf { totals[it] ?: 0L }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp)) {
        T("Your focus, growing quietly ✦", size = 22.sp, weight = FontWeight.Medium)
        Spacer(Modifier.height(24.dp))
        T("Today  ${todayMs / 60_000} min  ·  ${history.count { java.time.Instant.ofEpochMilli(it.startMs).atZone(java.time.ZoneId.systemDefault()).toLocalDate() == today }} sessions", size = 16.sp)
        Spacer(Modifier.height(32.dp))
        T("THIS WEEK  ·  ${weekMs / 60_000} min", size = 12.sp, color = LocalFocusColors.current.dim)
        FocusBars(days.map { totals[it] ?: 0L }, days.map { it.dayOfWeek.name.take(1) })
        Spacer(Modifier.height(30.dp))
        T("THIS MONTH  ·  ${monthMs / 60_000} min", size = 12.sp, color = LocalFocusColors.current.dim)
        FocusBars(monthDays.map { totals[it] ?: 0L }, monthDays.map { if (it.dayOfMonth % 5 == 0 || it.dayOfMonth == 1) it.dayOfMonth.toString() else "" })
        if (history.isEmpty()) T("Your first session will appear here.", Modifier.padding(top = 28.dp), color = LocalFocusColors.current.dim)
    }
}

@Composable
private fun FocusBars(values: List<Long>, labels: List<String>) {
    val colors = LocalFocusColors.current
    val max = values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    Row(Modifier.fillMaxWidth().height(130.dp).padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { index, value ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Box(Modifier.fillMaxWidth().height((8 + 86 * value.toFloat() / max).dp).background(if (value > 0) colors.fg else colors.line))
                Spacer(Modifier.height(7.dp))
                T(labels[index], size = 10.sp, color = colors.dim, maxLines = 1)
            }
        }
    }
}
