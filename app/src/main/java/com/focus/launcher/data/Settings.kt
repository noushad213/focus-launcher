package com.focus.launcher.data

import org.json.JSONArray
import org.json.JSONObject

enum class FontChoice(val label: String) { SANS("Sans"), SERIF("Serif"), MONO("Mono") }

/**
 * SPLIT: the clock on the left, one section on the right, a single vertical line between them and
 * no frame around it. RING: the clock inside a circle whose arc is the battery (or the day).
 */
enum class ClockStyle(val label: String) { SPLIT("Split"), RING("Ring"), PLAIN("Plain") }

/** What the right half of the split clock shows. The section shown there is not repeated below. */
enum class SplitSide(val label: String) { CALENDAR("Calendar"), SCREEN_TIME("Screen time") }

/** What the arc of the clock ring stands for. */
enum class RingMode(val label: String) { BATTERY("Battery level"), DAY("Day passed") }

/** How an app appears when it is opened from the launcher. */
enum class LaunchAnimation(val label: String) { FAST("Fast"), SYSTEM("System default") }

enum class HomeAlign(val label: String) { LEFT("Left"), CENTER("Center"), RIGHT("Right") }

/** Order of the app list in the drawer. */
enum class DrawerSort(val label: String) { ALPHA("A–Z"), MOST_USED("Most used"), RECENT("Recent") }

enum class TimeFormat(val label: String) { SYSTEM("Follow system"), H24("24-hour"), H12("12-hour") }

const val MAX_FAVORITES = 5

/** Corner shortcuts that resolve to whatever the phone's default dialer / camera is. */
const val SHORTCUT_PHONE = "auto:phone"
const val SHORTCUT_CAMERA = "auto:camera"

/** What a tap on the clock does. Anything that is not one of these is an [AppEntry.key] to open. */
const val TAP_ALARMS = "tap:alarms"
const val TAP_CALENDAR = "tap:calendar"
const val TAP_SCREEN_TIME = "tap:screentime"
const val TAP_BATTERY = "tap:battery"
const val TAP_NOTHING = "tap:none"

/** Shared action vocabulary for home gestures and edge pills. Arguments are action-specific. */
enum class ActionType(val label: String) {
    NONE("Nothing"), OPEN_APP("Open app"), APP_DRAWER("App drawer"), APP_SEARCH("Search apps"),
    NOTIFICATIONS("Notifications"), WEB_SEARCH("Web search"), OPEN_URL("Open URL"),
    LOCK_DEVICE("Lock device"), ALARMS("Alarms"), SELECTED_BROWSER("Open browser"), SETTINGS("Settings"), PILLS("Pill settings"),
    TODO("Todo"), NOTE("Note"), FOCUS_SETUP("Focus setup"), FOCUS_SESSION("Focus"),
}

data class TodoItem(val id: String, val text: String, val done: Boolean = false) {
    fun toJson() = JSONObject().put("id", id).put("text", text).put("done", done)

    companion object {
        fun fromJson(value: JSONObject): TodoItem? {
            val id = value.optString("id").takeIf { it.isNotBlank() } ?: return null
            val text = value.optString("text").trim().take(160).takeIf { it.isNotBlank() } ?: return null
            return TodoItem(id, text, value.optBoolean("done", false))
        }
    }
}

data class LauncherAction(val type: ActionType, val argument: String = "") {
    fun toJson() = JSONObject().put("type", type.name).put("argument", argument)

    companion object {
        fun fromJson(value: JSONObject?): LauncherAction? {
            if (value == null) return null
            val type = ActionType.entries.firstOrNull { it.name == value.optString("type") } ?: return null
            return LauncherAction(type, value.optString("argument"))
        }
    }
}

enum class GestureTrigger(val label: String) {
    SWIPE_UP("Swipe up"), SWIPE_DOWN("Swipe down"), SWIPE_LEFT("Swipe left"), SWIPE_RIGHT("Swipe right"),
    DOUBLE_TAP("Double tap"), LONG_PRESS("Long press"),
}

enum class PillEdge(val label: String) { LEFT("Left edge"), RIGHT("Right edge") }

data class EdgePill(
    val id: String,
    val name: String,
    val icon: String = "",
    val edge: PillEdge = PillEdge.RIGHT,
    val verticalPosition: Int = 50,
    val visiblePercent: Int = 70,
    val widthDp: Int = 88,
    val enabled: Boolean = true,
    val tapAction: LauncherAction = LauncherAction(ActionType.NONE),
    val longPressAction: LauncherAction = LauncherAction(ActionType.PILLS),
    val inwardSwipeAction: LauncherAction = LauncherAction(ActionType.NONE),
) {
    fun toJson() = JSONObject().put("id", id).put("name", name).put("icon", icon).put("edge", edge.name)
        .put("position", verticalPosition).put("visible", visiblePercent).put("width", widthDp)
        .put("enabled", enabled).put("tap", tapAction.toJson())
        .put("long", longPressAction.toJson()).put("swipe", inwardSwipeAction.toJson())

    companion object {
        fun fromJson(value: JSONObject): EdgePill? {
            val id = value.optString("id").takeIf { it.isNotBlank() } ?: return null
            return EdgePill(
                id = id,
                name = value.optString("name", "Pill").take(16),
                icon = value.optString("icon").take(2),
                edge = PillEdge.entries.firstOrNull { it.name == value.optString("edge") } ?: PillEdge.RIGHT,
                verticalPosition = value.optInt("position", 50).coerceIn(5, 95),
                visiblePercent = value.optInt("visible", 70).coerceIn(50, 80),
                widthDp = value.optInt("width", 88).coerceIn(72, 112),
                enabled = value.optBoolean("enabled", true),
                tapAction = LauncherAction.fromJson(value.optJSONObject("tap")) ?: LauncherAction(ActionType.NONE),
                longPressAction = LauncherAction.fromJson(value.optJSONObject("long")) ?: LauncherAction(ActionType.PILLS),
                inwardSwipeAction = LauncherAction.fromJson(value.optJSONObject("swipe")) ?: LauncherAction(ActionType.NONE),
            )
        }
    }
}

/**
 * Every user-facing preference of the launcher. Immutable; changed through [SettingsStore.update].
 *
 * App references ([favorites], [hidden], [renames], shortcuts) use [AppEntry.key].
 * Timers ([appLimits]) are keyed by package name because usage is tracked per package.
 */
data class Settings(
    // Appearance
    val dark: Boolean = true,
    /** Empty means the plain black or white background selected by [dark]. */
    val wallpaperId: String = "",
    val font: FontChoice = FontChoice.SANS,
    val textScale: Float = 1f,
    val hideStatusBar: Boolean = false,
    val launchAnimation: LaunchAnimation = LaunchAnimation.FAST,

    // Home
    val clockStyle: ClockStyle = ClockStyle.SPLIT,
    /** Right half of the split clock. CALENDAR needs [showCalendar]; without it screen time is shown. */
    val splitSide: SplitSide = SplitSide.CALENDAR,
    val ringMode: RingMode = RingMode.BATTERY,
    val clockTap: String = TAP_ALARMS,
    val timeFormat: TimeFormat = TimeFormat.SYSTEM,
    val showDate: Boolean = true,
    val showCalendar: Boolean = false,
    /** [CalendarInfo.key] of the single calendar shown on the home screen, or [CALENDAR_AUTO] / [CALENDAR_ALL]. */
    val calendarKey: String = CALENDAR_AUTO,
    /** The Mon-Sun strip with today marked. Off: the ring already carries the date. */
    val showWeekStrip: Boolean = false,
    /** Previous, play or pause, next, and what is playing, as a section of the home screen. */
    val showMusic: Boolean = false,
    /**
     * The music section leaves the home screen while nothing is playing and comes back with the
     * music (the owner's wish). Off = it is always there, as a way into the music app.
     */
    val musicAutoHide: Boolean = true,
    /** A few lines of the user's own as a section of the home screen; [note] is the text. */
    val showNote: Boolean = false,
    val note: String = "",
    /** [AppEntry.key] opened by a tap on the music section while nothing is playing. "" = not chosen yet: the first tap asks. */
    val musicApp: String = "",
    /** [AppEntry.key] of the notes app offered next to the note's title; "" = none. */
    val noteApp: String = "",
    /** With a notes app chosen: a link to one page in it (what its "copy link" gives). Blank = the app's own start screen. */
    val noteLink: String = "",
    val homeAlign: HomeAlign = HomeAlign.CENTER,
    val favorites: List<String> = emptyList(),
    val showShortcuts: Boolean = true,
    /** Seconds of inactivity on the home page before locking. 0 follows the phone's timeout. */
    val homeIdleLockSeconds: Int = 0,
    val leftShortcut: String = SHORTCUT_PHONE,
    val rightShortcut: String = SHORTCUT_CAMERA,

    // Drawer
    val autoKeyboard: Boolean = false,
    val autoLaunch: Boolean = false,
    val showRecentInstalls: Boolean = true,
    val showUsageInDrawer: Boolean = true,
    val drawerSort: DrawerSort = DrawerSort.ALPHA,
    val hidden: Set<String> = emptySet(),
    val renames: Map<String, String> = emptyMap(),

    // App timers
    val timersEnabled: Boolean = true,
    /** Daily minutes applied to every social app without its own limit. 0 = off. */
    val socialDefaultMin: Int = 30,
    /** Daily minutes applied to every game without its own limit. 0 = off. */
    val gameDefaultMin: Int = 30,
    /** Daily minutes applied to every video / streaming app without its own limit. 0 = off. */
    val videoDefaultMin: Int = 0,
    /** package -> daily minutes. 0 means "explicitly unlimited", overriding the category default. */
    val appLimits: Map<String, Int> = emptyMap(),
    val allowContinue: Boolean = true,
    val continueOptions: List<Int> = listOf(1, 5, 15),
    val allowBypass: Boolean = true,
    /** After "ignore the limit for today", still ask before every single open of that app. */
    val askAfterBypass: Boolean = true,
    /** Seconds the "continue" choices stay locked after the wall appears. */
    val frictionSeconds: Int = 5,
    /** Heads-up this many minutes before a limit runs out. 0 = off. */
    val warnMinutes: Int = 1,

    // Weekly review
    val weeklyEnabled: Boolean = true,
    val weekStartsMonday: Boolean = true,
    val reviewHour: Int = 20,

    // Gestures
    val swipeDownNotifications: Boolean = true,
    val swipeUpSearch: Boolean = true,
    /** Swipe towards the page left of home (finger moves right): the phone's web search. */
    val swipeRightSearch: Boolean = true,
    val doubleTapLock: Boolean = true,
    val gestureActions: Map<GestureTrigger, LauncherAction> = defaultGestureActions(),
    val edgePills: List<EdgePill> = listOf(focusPill()),
    val todos: List<TodoItem> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("v", SCHEMA)
        put("dark", dark)
        put("wallpaperId", wallpaperId)
        put("font", font.name)
        put("textScale", textScale.toDouble())
        put("hideStatusBar", hideStatusBar)
        put("launchAnimation", launchAnimation.name)

        put("clockStyle", clockStyle.name)
        put("splitSide", splitSide.name)
        put("ringMode", ringMode.name)
        put("clockTap", clockTap)
        put("timeFormat", timeFormat.name)
        put("showDate", showDate)
        put("showCalendar", showCalendar)
        put("calendarKey", calendarKey)
        put("showWeekStrip", showWeekStrip)
        put("showMusic", showMusic)
        put("musicAutoHide", musicAutoHide)
        put("showNote", showNote)
        put("note", note)
        put("musicApp", musicApp)
        put("noteApp", noteApp)
        put("noteLink", noteLink)
        put("homeAlign", homeAlign.name)
        put("favorites", JSONArray(favorites))
        put("showShortcuts", showShortcuts)
        put("homeIdleLockSeconds", homeIdleLockSeconds)
        put("leftShortcut", leftShortcut)
        put("rightShortcut", rightShortcut)

        put("autoKeyboard", autoKeyboard)
        put("autoLaunch", autoLaunch)
        put("showRecentInstalls", showRecentInstalls)
        put("showUsageInDrawer", showUsageInDrawer)
        put("drawerSort", drawerSort.name)
        put("hidden", JSONArray(hidden.toList()))
        put("renames", JSONObject(renames))

        put("timersEnabled", timersEnabled)
        put("socialDefaultMin", socialDefaultMin)
        put("gameDefaultMin", gameDefaultMin)
        put("videoDefaultMin", videoDefaultMin)
        put("appLimits", JSONObject(appLimits))
        put("allowContinue", allowContinue)
        put("continueOptions", JSONArray(continueOptions))
        put("allowBypass", allowBypass)
        put("askAfterBypass", askAfterBypass)
        put("frictionSeconds", frictionSeconds)
        put("warnMinutes", warnMinutes)

        put("weeklyEnabled", weeklyEnabled)
        put("weekStartsMonday", weekStartsMonday)
        put("reviewHour", reviewHour)

        put("swipeDownNotifications", swipeDownNotifications)
        put("swipeUpSearch", swipeUpSearch)
        put("swipeRightSearch", swipeRightSearch)
        put("doubleTapLock", doubleTapLock)
        put("gestureActions", JSONObject().apply { gestureActions.forEach { (trigger, action) -> put(trigger.name, action.toJson()) } })
        put("edgePills", JSONArray().apply { edgePills.forEach { put(it.toJson()) } })
        put("todos", JSONArray().apply { todos.forEach { put(it.toJson()) } })
    }

    companion object {
        /** Tolerant of missing keys, so older saved settings survive app updates. */
        /** Version of the stored settings. Bump it only when an old stored value has to be reinterpreted. */
        const val SCHEMA = 6

        private fun focusPill() = EdgePill(
            id = "builtin-focus",
            name = "Focus",
            tapAction = LauncherAction(ActionType.FOCUS_SESSION),
            inwardSwipeAction = LauncherAction(ActionType.FOCUS_SESSION),
        )

        fun fromJson(o: JSONObject): Settings {
            val d = Settings()
            return Settings(
                dark = o.optBoolean("dark", d.dark),
                wallpaperId = o.optString("wallpaperId", d.wallpaperId),
                font = enumOr(o.optString("font"), d.font),
                textScale = o.optDouble("textScale", d.textScale.toDouble()).toFloat().coerceIn(0.8f, 1.4f),
                hideStatusBar = o.optBoolean("hideStatusBar", d.hideStatusBar),
                launchAnimation = enumOr(o.optString("launchAnimation"), d.launchAnimation),

                // Schema 2 made the split clock the default, at the owner's request. A ring stored by an
                // older version was the old default, not a choice, so it moves along once; the ring stays
                // one tap away in Settings. A stored "Plain" was a choice and is kept.
                clockStyle = enumOr(o.optString("clockStyle"), d.clockStyle)
                    .let { if (o.optInt("v", 1) < 2 && it == ClockStyle.RING) ClockStyle.SPLIT else it },
                splitSide = enumOr(o.optString("splitSide"), d.splitSide),
                ringMode = enumOr(o.optString("ringMode"), d.ringMode),
                clockTap = o.optString("clockTap", d.clockTap).ifEmpty { d.clockTap },
                timeFormat = enumOr(o.optString("timeFormat"), d.timeFormat),
                showDate = o.optBoolean("showDate", d.showDate),
                showCalendar = o.optBoolean("showCalendar", d.showCalendar),
                calendarKey = o.optString("calendarKey").ifEmpty {
                    // Written for a few hours by an earlier build as a bare personal-calendar id.
                    when (val legacy = o.optLong("calendarId", -1L)) {
                        -1L -> CALENDAR_AUTO
                        -2L -> CALENDAR_ALL
                        else -> "p:$legacy"
                    }
                },
                showWeekStrip = o.optBoolean("showWeekStrip", d.showWeekStrip),
                showMusic = o.optBoolean("showMusic", d.showMusic),
                musicAutoHide = o.optBoolean("musicAutoHide", d.musicAutoHide),
                showNote = o.optBoolean("showNote", d.showNote),
                note = o.optString("note", d.note),
                musicApp = o.optString("musicApp", d.musicApp),
                noteApp = o.optString("noteApp", d.noteApp),
                noteLink = o.optString("noteLink", d.noteLink),
                homeAlign = enumOr(o.optString("homeAlign"), d.homeAlign),
                favorites = o.optJSONArray("favorites").strings().take(MAX_FAVORITES),
                showShortcuts = o.optBoolean("showShortcuts", d.showShortcuts),
                homeIdleLockSeconds = o.optInt("homeIdleLockSeconds", 0).takeIf { it in setOf(0, 30, 60, 120, 300) } ?: 0,
                leftShortcut = o.optString("leftShortcut", d.leftShortcut),
                rightShortcut = o.optString("rightShortcut", d.rightShortcut),

                autoKeyboard = o.optBoolean("autoKeyboard", d.autoKeyboard),
                autoLaunch = o.optBoolean("autoLaunch", d.autoLaunch),
                showRecentInstalls = o.optBoolean("showRecentInstalls", d.showRecentInstalls),
                showUsageInDrawer = o.optBoolean("showUsageInDrawer", d.showUsageInDrawer),
                drawerSort = enumOr(o.optString("drawerSort"), d.drawerSort),
                hidden = o.optJSONArray("hidden").strings().toSet(),
                renames = o.optJSONObject("renames").stringMap(),

                timersEnabled = o.optBoolean("timersEnabled", d.timersEnabled),
                socialDefaultMin = o.optInt("socialDefaultMin", d.socialDefaultMin),
                gameDefaultMin = o.optInt("gameDefaultMin", d.gameDefaultMin),
                videoDefaultMin = o.optInt("videoDefaultMin", d.videoDefaultMin),
                appLimits = o.optJSONObject("appLimits").intMap(),
                allowContinue = o.optBoolean("allowContinue", d.allowContinue),
                continueOptions = o.optJSONArray("continueOptions").ints().ifEmpty { d.continueOptions },
                allowBypass = o.optBoolean("allowBypass", d.allowBypass),
                askAfterBypass = o.optBoolean("askAfterBypass", d.askAfterBypass),
                frictionSeconds = o.optInt("frictionSeconds", d.frictionSeconds),
                warnMinutes = o.optInt("warnMinutes", d.warnMinutes),

                weeklyEnabled = o.optBoolean("weeklyEnabled", d.weeklyEnabled),
                weekStartsMonday = o.optBoolean("weekStartsMonday", d.weekStartsMonday),
                reviewHour = o.optInt("reviewHour", d.reviewHour).coerceIn(0, 23),

                swipeDownNotifications = o.optBoolean("swipeDownNotifications", d.swipeDownNotifications),
                swipeUpSearch = o.optBoolean("swipeUpSearch", d.swipeUpSearch),
                swipeRightSearch = o.optBoolean("swipeRightSearch", d.swipeRightSearch),
                doubleTapLock = o.optBoolean("doubleTapLock", d.doubleTapLock),
                gestureActions = o.optJSONObject("gestureActions")?.let { actions ->
                    val defaults = defaultGestureActions()
                    GestureTrigger.entries.forEach { trigger -> LauncherAction.fromJson(actions.optJSONObject(trigger.name))?.let { defaults[trigger] = it } }
                    defaults
                } ?: legacyGestureActions(o),
                edgePills = o.optJSONArray("edgePills").objects().mapNotNull(EdgePill::fromJson).take(8).map { pill ->
                    if (o.optInt("v", 1) >= 5) pill else pill.copy(
                        verticalPosition = if (pill.name.equals("todo", true) || pill.name.equals("note", true)) 50 else pill.verticalPosition,
                        visiblePercent = 70,
                        widthDp = 88,
                        tapAction = when {
                            pill.name.equals("todo", true) -> LauncherAction(ActionType.TODO)
                            pill.name.equals("note", true) -> LauncherAction(ActionType.NOTE)
                            else -> pill.tapAction
                        },
                    )
                }.map { pill ->
                    if (o.optInt("v", 1) < 6 && pill.name.equals("focus", true) && pill.tapAction.type == ActionType.FOCUS_SETUP)
                        pill.copy(tapAction = LauncherAction(ActionType.FOCUS_SESSION), inwardSwipeAction = LauncherAction(ActionType.FOCUS_SESSION))
                    else pill
                }.let { pills ->
                    if (o.optInt("v", 1) < 6 && pills.size < 8 && pills.none { it.tapAction.type == ActionType.FOCUS_SESSION || it.id == "builtin-focus" })
                        pills + focusPill().copy(verticalPosition = 78) else pills
                },
                todos = o.optJSONArray("todos").objects().mapNotNull(TodoItem::fromJson).take(100),
            )
        }

        private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
            enumValues<E>().firstOrNull { it.name == name } ?: fallback

        private fun JSONArray?.strings(): List<String> =
            if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotEmpty() } }

        private fun JSONArray?.ints(): List<Int> =
            if (this == null) emptyList() else (0 until length()).map { optInt(it) }.filter { it > 0 }

        private fun JSONArray?.objects(): List<JSONObject> =
            if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

        private fun defaultGestureActions() = mutableMapOf(
            GestureTrigger.SWIPE_UP to LauncherAction(ActionType.APP_SEARCH),
            GestureTrigger.SWIPE_DOWN to LauncherAction(ActionType.NOTIFICATIONS),
            GestureTrigger.SWIPE_LEFT to LauncherAction(ActionType.APP_DRAWER),
            GestureTrigger.SWIPE_RIGHT to LauncherAction(ActionType.WEB_SEARCH),
            GestureTrigger.DOUBLE_TAP to LauncherAction(ActionType.LOCK_DEVICE),
            GestureTrigger.LONG_PRESS to LauncherAction(ActionType.SETTINGS),
        )

        private fun legacyGestureActions(o: JSONObject) = defaultGestureActions().toMutableMap().apply {
            if (!o.optBoolean("swipeUpSearch", true)) this[GestureTrigger.SWIPE_UP] = LauncherAction(ActionType.NONE)
            if (!o.optBoolean("swipeDownNotifications", true)) this[GestureTrigger.SWIPE_DOWN] = LauncherAction(ActionType.NONE)
            if (!o.optBoolean("swipeRightSearch", true)) this[GestureTrigger.SWIPE_RIGHT] = LauncherAction(ActionType.NONE)
            if (!o.optBoolean("doubleTapLock", true)) this[GestureTrigger.DOUBLE_TAP] = LauncherAction(ActionType.NONE)
        }

        private fun JSONObject?.stringMap(): Map<String, String> {
            if (this == null) return emptyMap()
            val out = LinkedHashMap<String, String>()
            for (k in keys()) optString(k).takeIf { it.isNotEmpty() }?.let { out[k] = it }
            return out
        }

        private fun JSONObject?.intMap(): Map<String, Int> {
            if (this == null) return emptyMap()
            val out = LinkedHashMap<String, Int>()
            for (k in keys()) out[k] = optInt(k, 0)
            return out
        }
    }
}
