package com.focus.launcher.ui.settings

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focus.launcher.Graph
import com.focus.launcher.data.ActionType
import com.focus.launcher.data.AppEntry
import com.focus.launcher.data.EdgePill
import com.focus.launcher.data.GestureTrigger
import com.focus.launcher.data.LauncherAction
import com.focus.launcher.data.PillEdge
import com.focus.launcher.data.Settings
import com.focus.launcher.ui.components.AppPickerDialog
import com.focus.launcher.ui.components.ChoiceDialog
import com.focus.launcher.ui.components.ConfirmDialog
import com.focus.launcher.ui.components.FocusDialog
import com.focus.launcher.ui.components.MenuRow
import com.focus.launcher.ui.components.SettingRow
import com.focus.launcher.ui.components.T
import com.focus.launcher.ui.components.TextInputDialog
import com.focus.launcher.ui.components.ToggleRow
import com.focus.launcher.ui.theme.LocalFocusColors
import java.util.UUID

private fun update(transform: (Settings) -> Settings) = Graph.settings.update(transform)

@Composable
internal fun GesturesPage(settings: Settings, status: SetupStatus, onBack: () -> Unit, go: (String) -> Unit) {
    val apps by Graph.apps.apps.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<Pair<GestureTrigger, LauncherAction>?>(null) }
    Page("Gestures", onBack) {
        Note("Choose what each gesture does. Swipe left opens the drawer by default; long press opens Settings.")
        GestureTrigger.entries.forEach { trigger ->
            val action = settings.gestureActions[trigger] ?: LauncherAction(ActionType.NONE)
            SettingRow(trigger.label, value = actionLabel(action, apps), onClick = { selected = trigger to action })
        }
        ToggleRow("Keyboard opens with the drawer", settings.autoKeyboard, subtitle = "Start typing the moment you swipe to your apps.") { v -> update { it.copy(autoKeyboard = v) } }
        if (settings.gestureActions[GestureTrigger.DOUBLE_TAP]?.type == ActionType.LOCK_DEVICE && !status.timerService) {
            Note("The Focus timer service is off, so double tap cannot lock yet.  Open setup  →") { go(Routes.SETUP) }
        }
    }
    selected?.let { (trigger, action) ->
        ActionEditor("${trigger.label} action", action, apps, { selected = null }) { next ->
            update { it.copy(gestureActions = it.gestureActions + (trigger to next)) }
            selected = null
        }
    }
}

private fun actionLabel(action: LauncherAction, apps: List<AppEntry>): String = when (action.type) {
    ActionType.OPEN_APP -> apps.firstOrNull { it.key == action.argument }?.label ?: "Choose app"
    ActionType.OPEN_URL -> action.argument.ifBlank { "Set URL" }
    ActionType.SELECTED_BROWSER -> apps.firstOrNull { it.key == action.argument }?.label ?: "Choose browser"
    else -> action.type.label
}

@Composable
private fun ActionEditor(title: String, current: LauncherAction, apps: List<AppEntry>, onDismiss: () -> Unit, onSet: (LauncherAction) -> Unit) {
    val context = LocalContext.current
    var type by remember { mutableStateOf<ActionType?>(null) }
    var choosingApp by remember { mutableStateOf(false) }
    var editingUrl by remember { mutableStateOf(false) }
    FocusDialog(onDismiss, title) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
        ActionType.entries.forEach { option ->
            MenuRow(option.label, selected = current.type == option, onClick = {
                type = option
                when (option) {
                    ActionType.OPEN_APP, ActionType.SELECTED_BROWSER -> choosingApp = true
                    ActionType.OPEN_URL -> editingUrl = true
                    else -> onSet(LauncherAction(option))
                }
            })
        }
        }
    }
    val browserPackages = remember(context) {
        context.packageManager.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse("https://focus.invalid")).addCategory(Intent.CATEGORY_BROWSABLE), PackageManager.MATCH_DEFAULT_ONLY)
            .mapTo(HashSet()) { it.activityInfo.packageName }
    }
    val choices = if (type == ActionType.SELECTED_BROWSER) apps.filter { it.packageName in browserPackages } else apps
    if (choosingApp) AppPickerDialog(if (type == ActionType.SELECTED_BROWSER) "Choose browser" else "Choose app", choices, { choosingApp = false }, onPick = { app ->
        onSet(LauncherAction(type ?: ActionType.OPEN_APP, app.key))
        choosingApp = false
    })
    if (editingUrl) TextInputDialog("Open URL", current.argument, "https://…", { editingUrl = false }) { url ->
        onSet(LauncherAction(ActionType.OPEN_URL, url))
        editingUrl = false
    }
}

@Composable
internal fun PillsPage(settings: Settings, apps: List<AppEntry>, onBack: () -> Unit) {
    var editing by remember { mutableStateOf<EdgePill?>(null) }
    var deleting by remember { mutableStateOf<EdgePill?>(null) }
    var adding by remember { mutableStateOf(false) }
    var namingCustom by remember { mutableStateOf(false) }
    fun save(pill: EdgePill) = update { s -> s.copy(edgePills = (s.edgePills.filterNot { it.id == pill.id } + pill).sortedBy { it.verticalPosition }) }
    fun addPreset(name: String, action: ActionType) {
        val pill = EdgePill(
            id = UUID.randomUUID().toString(),
            name = name,
            verticalPosition = (50 + settings.edgePills.size * 9).coerceAtMost(78),
            tapAction = LauncherAction(action),
            inwardSwipeAction = LauncherAction(action),
        )
        save(pill)
        editing = pill
        adding = false
    }
    fun reorder(items: List<EdgePill>) = update { current ->
        current.copy(edgePills = items.mapIndexed { i, pill -> pill.copy(verticalPosition = (50 + i * 9).coerceAtMost(78)) })
    }
    Page("Pills", onBack) {
        Note("Small tabs stay partly beyond the display edge. Move them with the arrows; each pill has separate tap, hold and inward-swipe actions.")
        settings.edgePills.forEachIndexed { index, pill ->
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                SettingRow(pill.name, modifier = Modifier.weight(1f), subtitle = "${pill.edge.label} · ${actionLabel(pill.tapAction, apps)}", value = if (pill.enabled) "On" else "Off", onClick = { editing = pill })
                T("↑", Modifier.clickable(enabled = index > 0) { val items = settings.edgePills.toMutableList(); val item = items.removeAt(index); items.add(index - 1, item); reorder(items) }.padding(8.dp))
                T("↓", Modifier.clickable(enabled = index < settings.edgePills.lastIndex) { val items = settings.edgePills.toMutableList(); val item = items.removeAt(index); items.add(index + 1, item); reorder(items) }.padding(8.dp))
                T("×", Modifier.clickable { deleting = pill }.padding(8.dp), color = LocalFocusColors.current.dim)
            }
        }
        SettingRow("+ Add pill", value = "${settings.edgePills.size} / 8", enabled = settings.edgePills.size < 8, onClick = { adding = true })
    }
    if (adding) FocusDialog({ adding = false }, "Add pill") {
        T("Built-in pills use launcher data and work immediately.", Modifier.padding(bottom = 8.dp), size = 13.sp, color = LocalFocusColors.current.dim)
        MenuRow("Todo", onClick = { addPreset("Todo", ActionType.TODO) })
        MenuRow("Note", onClick = { addPreset("Note", ActionType.NOTE) })
        MenuRow("Apps", onClick = { addPreset("Apps", ActionType.APP_DRAWER) })
        MenuRow("Search", onClick = { addPreset("Search", ActionType.APP_SEARCH) })
        MenuRow("Focus", onClick = { addPreset("Focus", ActionType.FOCUS_SESSION) })
        MenuRow("Custom…", onClick = { adding = false; namingCustom = true })
    }
    if (namingCustom) TextInputDialog("Custom pill", "", "Name", { namingCustom = false }) { name ->
        if (name.isNotBlank()) { val pill = EdgePill(UUID.randomUUID().toString(), name.take(16), verticalPosition = (50 + settings.edgePills.size * 9).coerceAtMost(78)); save(pill); editing = pill }
        namingCustom = false
    }
    deleting?.let { pill ->
        ConfirmDialog("Remove pill", "Remove ${pill.name}?", "Remove", { deleting = null }) {
            update { it.copy(edgePills = it.edgePills.filterNot { item -> item.id == pill.id }) }
            deleting = null
        }
    }
    editing?.let { pill -> PillEditor(pill, apps, { editing = null }, ::save, onDelete = { update { it.copy(edgePills = it.edgePills.filterNot { p -> p.id == pill.id }) }; editing = null }) }
}

@Composable
private fun PillEditor(pill: EdgePill, apps: List<AppEntry>, close: () -> Unit, save: (EdgePill) -> Unit, onDelete: () -> Unit) {
    var name by remember(pill.id) { mutableStateOf(pill.name) }
    var icon by remember(pill.id) { mutableStateOf(pill.icon) }
    var edge by remember(pill.id) { mutableStateOf(pill.edge) }
    var enabled by remember(pill.id) { mutableStateOf(pill.enabled) }
    var position by remember(pill.id) { mutableIntStateOf(pill.verticalPosition) }
    var visible by remember(pill.id) { mutableIntStateOf(pill.visiblePercent) }
    var width by remember(pill.id) { mutableIntStateOf(pill.widthDp) }
    var tap by remember(pill.id) { mutableStateOf(pill.tapAction) }
    var hold by remember(pill.id) { mutableStateOf(pill.longPressAction) }
    var swipe by remember(pill.id) { mutableStateOf(pill.inwardSwipeAction) }
    var editingAction by remember { mutableStateOf<Pair<String, LauncherAction>?>(null) }
    var naming by remember { mutableStateOf(false) }
    var choosingIcon by remember { mutableStateOf(false) }
    var choosingEdge by remember { mutableStateOf(false) }
    var editingPosition by remember { mutableStateOf(false) }
    var askingDelete by remember { mutableStateOf(false) }
    FocusDialog(close, title = "Pill") {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
        SettingRow("Name", value = name, onClick = { naming = true })
        SettingRow("Icon", value = icon.ifBlank { "None" }, onClick = { choosingIcon = true })
        SettingRow("Edge", value = edge.label, onClick = { choosingEdge = true })
        ToggleRow("Enabled", enabled) { enabled = it }
        SettingRow("Vertical position", subtitle = "5% top · 95% bottom", value = "$position%", onClick = { editingPosition = true })
        SettingRow("Visible width", value = "$visible%", onClick = { visible = if (visible >= 80) 50 else visible + 10 })
        SettingRow("Pill size", value = "${width}dp", onClick = { width = if (width >= 112) 72 else width + 8 })
        SettingRow("Tap", value = actionLabel(tap, apps), onClick = { editingAction = "Tap" to tap })
        SettingRow("Long press", value = actionLabel(hold, apps), onClick = { editingAction = "Long press" to hold })
        SettingRow("Swipe inward", value = actionLabel(swipe, apps), onClick = { editingAction = "Swipe inward" to swipe })
        SettingRow("Remove pill", onClick = { askingDelete = true })
        }
        SettingRow("Save", onClick = { save(pill.copy(name = name.ifBlank { "Pill" }, icon = icon, edge = edge, enabled = enabled, verticalPosition = position, visiblePercent = visible, widthDp = width, tapAction = tap, longPressAction = hold, inwardSwipeAction = swipe)); close() })
    }
    if (naming) TextInputDialog("Pill name", name, "Name", { naming = false }) { name = it.take(16) }
    if (choosingIcon) TextInputDialog("Pill icon", icon, "Optional symbol", { choosingIcon = false }) { icon = it.take(2) }
    if (choosingEdge) ChoiceDialog("Edge", PillEdge.entries.map { it to it.label }, edge, { choosingEdge = false }, onSelect = { edge = it; choosingEdge = false })
    if (editingPosition) TextInputDialog("Vertical position", position.toString(), "5–95", { editingPosition = false }) { value ->
        value.trim().toIntOrNull()?.let { position = it.coerceIn(5, 95) }
        editingPosition = false
    }
    if (askingDelete) ConfirmDialog("Remove pill", "Remove ${pill.name}?", "Remove", { askingDelete = false }, onDelete)
    editingAction?.let { (title, action) ->
        ActionEditor(title, action, apps, { editingAction = null }) { next ->
            when (title) { "Tap" -> tap = next; "Long press" -> hold = next; else -> swipe = next }
            editingAction = null
        }
    }
}




