package com.focus.launcher.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Schema 2 made the split clock the default. Settings are stored with every field written out, so
 * a stored "RING" from before is the old default rather than a choice, and moves along once.
 * Everything a user did choose has to survive.
 */
class SettingsMigrationTest {

    private fun stored(version: Int?, style: String?): JSONObject = Settings().toJson().apply {
        if (version == null) remove("v") else put("v", version)
        if (style == null) remove("clockStyle") else put("clockStyle", style)
    }

    @Test fun `a fresh install gets the split clock`() {
        assertEquals(ClockStyle.SPLIT, Settings().clockStyle)
        assertEquals(ClockStyle.SPLIT, Settings.fromJson(JSONObject()).clockStyle)
    }

    @Test fun `a ring stored before schema 2 becomes the split clock`() {
        assertEquals(ClockStyle.SPLIT, Settings.fromJson(stored(version = null, style = "RING")).clockStyle)
        assertEquals(ClockStyle.SPLIT, Settings.fromJson(stored(version = 1, style = "RING")).clockStyle)
    }

    @Test fun `a plain clock chosen before schema 2 is kept`() {
        assertEquals(ClockStyle.PLAIN, Settings.fromJson(stored(version = null, style = "PLAIN")).clockStyle)
    }

    @Test fun `a ring chosen after the change is kept`() {
        val chosen = Settings(clockStyle = ClockStyle.RING)
        assertEquals(ClockStyle.RING, Settings.fromJson(chosen.toJson()).clockStyle)
    }

    @Test fun `settings survive a round trip`() {
        val s = Settings(clockStyle = ClockStyle.SPLIT, splitSide = SplitSide.SCREEN_TIME, showCalendar = true, doubleTapLock = false, musicAutoHide = false)
        assertEquals(s, Settings.fromJson(s.toJson()))
    }

    @Test fun `home idle lock is optional and survives a round trip`() {
        assertEquals(0, Settings.fromJson(JSONObject()).homeIdleLockSeconds)
        assertEquals(120, Settings.fromJson(Settings(homeIdleLockSeconds = 120).toJson()).homeIdleLockSeconds)
        assertEquals(0, Settings.fromJson(JSONObject().put("homeIdleLockSeconds", -1)).homeIdleLockSeconds)
    }

    @Test fun `an install from before the music section could hide gets the hiding`() {
        val old = Settings(showMusic = true).toJson().apply { remove("musicAutoHide") }
        assertEquals(true, Settings.fromJson(old).musicAutoHide)
    }

    @Test fun `an unknown stored value falls back to the default`() {
        assertEquals(SplitSide.CALENDAR, Settings.fromJson(stored(version = 2, style = "SPLIT").put("splitSide", "MAIL")).splitSide)
    }

    @Test fun `legacy gesture switches become equivalent actions`() {
        val old = Settings().toJson().apply { remove("gestureActions"); put("swipeDownNotifications", false) }
        val migrated = Settings.fromJson(old)
        assertEquals(ActionType.NONE, migrated.gestureActions.getValue(GestureTrigger.SWIPE_DOWN).type)
        assertEquals(ActionType.APP_SEARCH, migrated.gestureActions.getValue(GestureTrigger.SWIPE_UP).type)
        assertEquals(ActionType.LOCK_DEVICE, migrated.gestureActions.getValue(GestureTrigger.DOUBLE_TAP).type)
        assertEquals(ActionType.SETTINGS, migrated.gestureActions.getValue(GestureTrigger.LONG_PRESS).type)
    }

    @Test fun `gesture actions and edge pills survive a round trip`() {
        val pill = EdgePill(
            id = "note", name = "NOTE", edge = PillEdge.LEFT, verticalPosition = 42,
            tapAction = LauncherAction(ActionType.OPEN_URL, "https://example.org"),
            inwardSwipeAction = LauncherAction(ActionType.OPEN_APP, "pkg/.Main"),
        )
        val settings = Settings(
            gestureActions = Settings().gestureActions + (GestureTrigger.DOUBLE_TAP to LauncherAction(ActionType.OPEN_APP, "pkg/.Main")),
            edgePills = listOf(pill),
        )
        assertEquals(settings, Settings.fromJson(settings.toJson()))
    }

    @Test fun `explicit no action and unknown action values stay safe`() {
        val stored = Settings().toJson()
        stored.getJSONObject("gestureActions").put("LONG_PRESS", LauncherAction(ActionType.NONE).toJson())
        stored.getJSONObject("gestureActions").put("SWIPE_RIGHT", JSONObject().put("type", "UNKNOWN"))
        val restored = Settings.fromJson(stored)
        assertEquals(ActionType.NONE, restored.gestureActions.getValue(GestureTrigger.LONG_PRESS).type)
        assertEquals(ActionType.WEB_SEARCH, restored.gestureActions.getValue(GestureTrigger.SWIPE_RIGHT).type)
    }
}
