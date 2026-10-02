package com.focus.launcher

import android.content.Context
import android.os.Bundle
import android.os.Build
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focus.launcher.data.FocusSessions
import com.focus.launcher.data.FocusMode
import com.focus.launcher.data.AppEntry
import com.focus.launcher.data.focusMinutesByDay
import com.focus.launcher.ui.components.FocusButton
import com.focus.launcher.ui.components.ConfirmDialog
import com.focus.launcher.ui.components.Hairline
import com.focus.launcher.ui.components.T
import com.focus.launcher.ui.theme.FocusTheme
import com.focus.launcher.ui.theme.LocalFocusColors
import com.focus.launcher.ui.theme.applyFocusWindow
import com.focus.launcher.ui.components.focusTextStyle
import androidx.compose.ui.graphics.SolidColor
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.temporal.WeekFields

class FocusSessionActivity : ComponentActivity() {
    private val sessions by lazy { FocusSessions(this) }
    private var active by mutableStateOf(false)
    private var confirming by mutableStateOf(false)
    private var exitAfterStop by mutableStateOf(false)
    private var revision by mutableStateOf(0)
    private var originalBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        originalBrightness = window.attributes.screenBrightness
        sessions.finishIfExpired()
        active = sessions.isActive()
        setContent {
            val settings by Graph.settings.flow.collectAsStateWithLifecycle()
            val apps by Graph.apps.apps.collectAsStateWithLifecycle()
            LaunchedEffect(settings.dark, active) { updateFocusDisplay(settings.dark) }
            FocusTheme(if (active) settings.copy(dark = true) else settings) {
                FocusSessionScreen(active, sessions, apps, revision, ::start, ::askToStop, ::askToLeave, ::stop, ::finish,
                    confirming, exitAfterStop,
                    onTick = {
                        if (sessions.finishIfExpired()) { active = false; revision++ }
                    }, onToggle = { pkg ->
                        val chosen = sessions.selectedPackages.toMutableSet()
                        if (pkg in chosen) chosen.remove(pkg) else if (chosen.size < FocusSessions.MAX_APPS) chosen.add(pkg)
                        sessions.choose(chosen)
                        revision++
                    }, onLaunch = { app -> Graph.apps.launch(app) }, cancelConfirm = { confirming = false })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (sessions.finishIfExpired()) revision++
        active = sessions.isActive()
    }

    private fun start(mode: FocusMode) {
        if (mode == FocusMode.SANDBOX && !com.focus.launcher.util.Perms.isTimerServiceEnabled(this)) {
            android.widget.Toast.makeText(this, "Enable Focus app timers in Accessibility settings first", android.widget.Toast.LENGTH_LONG).show()
            startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        sessions.start(mode); active = true; updateFocusDisplay(true)
    }
    private fun stop() { sessions.stop(); active = false; revision++; updateFocusDisplay(Graph.settings.value.dark) }
    private fun askToStop() { exitAfterStop = false; confirming = true }
    private fun askToLeave() {
        when {
            !active -> finish()
            sessions.activeMode == FocusMode.STOPWATCH -> { exitAfterStop = true; confirming = true }
        }
    }

    private fun updateFocusDisplay(preferredDark: Boolean) {
        applyFocusWindow(if (active) true else preferredDark)
        val attributes = window.attributes
        attributes.screenBrightness = if (active) 0.08f else originalBrightness
        if (active) {
            // A still countdown has no use for the launcher's preferred high refresh rate.
            val screen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay
            }
            val current = screen?.mode
            val slower = screen?.supportedModes?.filter {
                current != null && it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight
            }?.minByOrNull { it.refreshRate }
            if (slower != null) attributes.preferredDisplayModeId = slower.modeId
        }
        window.attributes = attributes
    }

    @Deprecated("Use BackHandler for Compose navigation")
    override fun onBackPressed() = askToLeave()

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        val interactive = (getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
        if (!hasFocus && active && sessions.activeMode == FocusMode.STOPWATCH && !confirming && interactive) stop()
    }

    override fun onStop() {
        super.onStop()
        val interactive = (getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
        if (active && sessions.activeMode == FocusMode.STOPWATCH && interactive && !confirming) stop()
    }
}

@Composable
private fun FocusSessionScreen(
    active: Boolean,
    sessions: FocusSessions,
    apps: List<AppEntry>,
    revision: Int,
    onStart: (FocusMode) -> Unit,
    onStopRequest: () -> Unit,
    onBack: () -> Unit,
    onStop: () -> Unit,
    finish: () -> Unit,
    confirming: Boolean,
    exitAfterStop: Boolean,
    onTick: () -> Unit,
    onToggle: (String) -> Unit,
    onLaunch: (AppEntry) -> Unit,
    cancelConfirm: () -> Unit,
) {
    val colors = LocalFocusColors.current
    var tab by remember { mutableStateOf(0) }
    var choice by remember { mutableStateOf(FocusMode.STOPWATCH) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active) {
        while (active) { now = System.currentTimeMillis(); onTick(); delay(1_000) }
    }
    BackHandler { onBack() }
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!active || sessions.activeMode == FocusMode.STOPWATCH)
                T("←", Modifier.clickable(onClick = onBack).padding(end = 24.dp), size = 24.sp)
            T("Focus", Modifier.weight(1f), size = 22.sp, weight = FontWeight.Medium)
            if (!active) T("Analysis", Modifier.clickable { tab = 1 }.padding(8.dp), size = 14.sp)
        }
        Hairline()
        if (active) {
            if (sessions.activeMode == FocusMode.SANDBOX) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        T("FOCUS SANDBOX", size = 11.sp, color = colors.dim)
                        Spacer(Modifier.height(28.dp))
                        val remainingMs = ((sessions.activeEnd ?: now) - now).coerceAtLeast(0)
                        val remainingSeconds = (remainingMs + 999) / 1000
                        FocusCountdownRing(remainingMs.toFloat() / FocusSessions.DURATION_MS,
                            "%02d:%02d".format(remainingSeconds / 60, remainingSeconds % 60))
                        Spacer(Modifier.height(24.dp))
                        T("Only your chosen apps until time is up", color = colors.dim)
                        Spacer(Modifier.height(28.dp))
                        apps.filter { it.packageName in sessions.allowedPackages }.distinctBy { it.packageName }.forEach { app ->
                            T(app.label, Modifier.clickable { onLaunch(app) }.padding(12.dp), size = 18.sp)
                        }
                    }
                }
                T("Ends automatically after 25 minutes", Modifier.fillMaxWidth().padding(bottom = 28.dp), size = 13.sp, color = colors.dim, align = TextAlign.Center)
            } else {
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
            }
        } else if (tab == 0) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                T("Choose your focus", size = 24.sp, weight = FontWeight.Medium)
                Spacer(Modifier.height(20.dp))
                FocusModeRow("Open timer", "Count up and stop whenever you are done.", choice == FocusMode.STOPWATCH) { choice = FocusMode.STOPWATCH }
                Spacer(Modifier.height(10.dp))
                FocusModeRow("25-minute sandbox", "Count down with up to three allowed apps.", choice == FocusMode.SANDBOX) { choice = FocusMode.SANDBOX }
                Spacer(Modifier.height(24.dp))
                if (choice == FocusMode.SANDBOX) {
                    FocusAppPicker(apps, sessions.selectedPackages, revision, onToggle, Modifier.fillMaxWidth().weight(1f))
                } else {
                    Spacer(Modifier.weight(1f))
                    T("Your time starts when you tap Focus.", color = colors.dim, align = TextAlign.Center)
                    Spacer(Modifier.weight(1f))
                }
                FocusButton(if (choice == FocusMode.SANDBOX) "Start 25 minutes" else "Focus",
                    Modifier.fillMaxWidth(), primary = true, onClick = { onStart(choice) })
                if (choice == FocusMode.SANDBOX) {
                    Spacer(Modifier.height(12.dp))
                    T("A voluntary guard; Android settings and calls stay available.", size = 12.sp, color = colors.dim, align = TextAlign.Center)
                }
            }
        } else {
            FocusAnalysis(sessions, revision, Modifier.weight(1f))
            FocusButton("Set up focus", Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp), primary = true, onClick = { tab = 0 })
        }
    }
    if (confirming) ConfirmDialog("End focus?", "Your focused time will be saved to Analysis.", if (exitAfterStop) "Stop & leave" else "Stop & save", cancelConfirm) {
        onStop()
        if (exitAfterStop) finish() else tab = 1
    }
}

@Composable
private fun FocusModeRow(title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalFocusColors.current
    Column(Modifier.fillMaxWidth().border(1.dp, if (selected) colors.fg else colors.line).clickable(onClick = onClick).padding(16.dp)) {
        T((if (selected) "●  " else "○  ") + title, weight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        T(description, size = 13.sp, color = colors.dim)
    }
}

@Composable
private fun FocusCountdownRing(progress: Float, time: String) {
    val colors = LocalFocusColors.current
    Box(Modifier.size(248.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 8.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(colors.line, -90f, 360f, false, topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(colors.fg, -90f, 360f * progress.coerceIn(0f, 1f), false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round))
        }
        T(time, size = 48.sp, weight = FontWeight.Medium)
    }
}

@Composable
private fun FocusAppPicker(
    apps: List<AppEntry>,
    selected: Set<String>,
    revision: Int,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalFocusColors.current
    var query by remember { mutableStateOf("") }
    val candidates = remember(apps, query, revision) {
        apps.filter { Graph.apps.canLimit(it.packageName) }
            .distinctBy { it.packageName }
            .filter { it.label.contains(query, ignoreCase = true) || it.packageName in selected }
            .sortedWith(compareByDescending<AppEntry> { it.packageName in selected }.thenBy { it.label.lowercase() })
    }
    Column(modifier.padding(horizontal = 24.dp)) {
        T("ALLOWED APPS  ${selected.size}/3", size = 12.sp, color = colors.dim)
        Spacer(Modifier.height(8.dp))
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().border(1.dp, colors.dim).padding(12.dp),
            singleLine = true,
            textStyle = focusTextStyle(size = 16.sp),
            cursorBrush = SolidColor(colors.fg),
            decorationBox = { inner -> Box {
                if (query.isEmpty()) T("Find an app", color = colors.dim)
                inner()
            } },
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            candidates.forEach { app ->
                val chosen = app.packageName in selected
                Row(Modifier.fillMaxWidth().clickable { onToggle(app.packageName) }.padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    T(app.label, Modifier.weight(1f), size = 16.sp)
                    T(if (chosen) "✓" else "+", color = if (chosen) colors.fg else colors.dim)
                }
                Hairline()
            }
        }
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
