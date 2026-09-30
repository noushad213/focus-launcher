package com.focus.launcher.util

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.core.net.toUri
import com.focus.launcher.Graph
import com.focus.launcher.service.FocusAccessibilityService

/** Checks for, and deep links to, the handful of system switches the launcher depends on. */
object Perms {
    fun isDefaultLauncher(context: Context): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == context.packageName
    }

    fun hasUsageAccess(): Boolean = Graph.usage.hasAccess()

    /** True when the user switched the timer service on in Accessibility settings. */
    fun isTimerServiceEnabled(context: Context): Boolean {
        if (FocusAccessibilityService.isRunning) return true
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val mine = ComponentName(context, FocusAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == mine }
    }

    fun canPostNotifications(context: Context): Boolean {
        val enabled = context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() ?: false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return enabled
        return enabled && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    // ---- deep links --------------------------------------------------------------------------

    fun openUsageAccess(context: Context) = start(
        context,
        // The general list is reliable across OEM Settings apps. Several phones accept the
        // package deep link and then immediately close it without showing a switch.
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, "package:${context.packageName}".toUri()),
        Intent(Settings.ACTION_SETTINGS),
    )

    fun openAccessibility(context: Context) = start(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    fun openHomeSettings(context: Context) = start(
        context,
        Intent(Settings.ACTION_HOME_SETTINGS),
        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )

    fun openNotificationSettings(context: Context) = start(
        context,
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        appDetails(context),
    )

    fun openAppDetails(context: Context) = start(context, appDetails(context))

    private fun appDetails(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())

    /** Tries each intent in order until one can be started. [options] carries the launch animation. */
    fun start(context: Context, vararg candidates: Intent, options: Bundle? = null): Boolean {
        for (intent in candidates) {
            try {
                if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent, options)
                return true
            } catch (_: Exception) {
            }
        }
        return false
    }
}
