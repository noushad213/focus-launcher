package com.focus.launcher.ui

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.widget.Toast
import com.focus.launcher.SettingsActivity
import com.focus.launcher.ToolActivity
import com.focus.launcher.ui.settings.Routes
import androidx.core.net.toUri
import com.focus.launcher.data.ActionType
import com.focus.launcher.data.AppEntry
import com.focus.launcher.data.LauncherAction
import com.focus.launcher.service.FocusAccessibilityService
import com.focus.launcher.util.Perms
import kotlinx.coroutines.CoroutineScope

/** Executes shared gesture and pill actions through the launch paths already used by Focus. */
fun executeAction(
    context: Context,
    scope: CoroutineScope,
    action: LauncherAction,
    apps: List<AppEntry>,
    openDrawer: (focusSearch: Boolean) -> Unit,
) {
    when (action.type) {
        ActionType.NONE -> Unit
        ActionType.OPEN_APP -> apps.firstOrNull { it.key == action.argument }?.let { launchApp(context, scope, it) }
            ?: unavailable(context)
        ActionType.APP_DRAWER -> openDrawer(false)
        ActionType.APP_SEARCH -> openDrawer(true)
        ActionType.SETTINGS -> context.startActivity(SettingsActivity.intent(context, null))
        ActionType.PILLS -> context.startActivity(SettingsActivity.intent(context, Routes.PILLS))
        ActionType.TODO -> context.startActivity(ToolActivity.intent(context, ToolActivity.TODO))
        ActionType.NOTE -> context.startActivity(ToolActivity.intent(context, ToolActivity.NOTE))
        ActionType.FOCUS_SETUP -> context.startActivity(SettingsActivity.intent(context, Routes.SETUP))
        ActionType.NOTIFICATIONS -> if (!FocusAccessibilityService.openNotifications()) {
            try {
                @Suppress("PrivateApi", "WrongConstant")
                val manager = context.getSystemService("statusbar")
                Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(manager)
            } catch (_: Exception) { unavailable(context) }
        }
        ActionType.WEB_SEARCH -> openWebSearch(context)
        ActionType.OPEN_URL -> {
            val raw = action.argument.trim()
            val url = if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) raw else "https://$raw"
            val uri = url.toUri()
            if (raw.isBlank() || uri.host.isNullOrBlank() || uri.scheme !in listOf("http", "https") ||
                !Perms.start(context, Intent(Intent.ACTION_VIEW, uri), options = launchOptions(context))) unavailable(context)
        }
        ActionType.LOCK_DEVICE -> if (!FocusAccessibilityService.lockScreen()) {
            Toast.makeText(context, "Turn on the Focus timer service to lock", Toast.LENGTH_SHORT).show()
        }
        ActionType.ALARMS -> if (!Perms.start(context, Intent(AlarmClock.ACTION_SHOW_ALARMS), options = launchOptions(context))) unavailable(context)
        ActionType.SELECTED_BROWSER -> {
            apps.firstOrNull { it.key == action.argument || it.packageName == action.argument }
                ?.let { launchApp(context, scope, it) } ?: unavailable(context)
        }
    }
}

private fun unavailable(context: Context) {
    Toast.makeText(context, "Action isn't available", Toast.LENGTH_SHORT).show()
}
