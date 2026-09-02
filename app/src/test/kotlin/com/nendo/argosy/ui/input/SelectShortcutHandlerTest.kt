package com.nendo.argosy.ui.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectShortcutHandlerTest {
    private val handler = SelectShortcutHandler().apply {
        configure("quick_menu", "quick_settings")
    }
    private val events = mutableListOf<GamepadEvent>()

    private fun press(event: GamepadEvent, repeat: Int = 0): Boolean =
        handler.handle(event, KeyEvent.ACTION_DOWN, repeat) { events.add(it) }

    private fun release(event: GamepadEvent): Boolean =
        handler.handle(event, KeyEvent.ACTION_UP, 0) { events.add(it) }

    @Test
    fun standaloneSelectFiresOnlyOnRelease() {
        assertTrue(press(GamepadEvent.Select))
        assertTrue(events.isEmpty())
        release(GamepadEvent.Select)
        assertEquals(listOf(GamepadEvent.Select), events)
    }

    @Test
    fun selectRepeatsAfterAChordDoNotRestoreStandaloneSelect() {
        press(GamepadEvent.Select)
        press(GamepadEvent.NextSection)
        press(GamepadEvent.Select, repeat = 1)
        press(GamepadEvent.NextSection, repeat = 1)
        release(GamepadEvent.NextSection)
        release(GamepadEvent.Select)
        assertEquals(listOf(GamepadEvent.RightStickClick), events)
    }

    @Test
    fun eitherReleaseOrderSuppressesStandaloneSelect() {
        for (selectFirst in listOf(true, false)) {
            events.clear()
            press(GamepadEvent.Select)
            press(GamepadEvent.PrevSection)
            val releases = listOf(GamepadEvent.Select, GamepadEvent.PrevSection)
            (if (selectFirst) releases else releases.reversed()).forEach { release(it) }
            assertEquals(listOf(GamepadEvent.LeftStickClick), events)
        }
    }

    @Test
    fun unboundButtonsKeepExistingSingleScreenBehavior() {
        handler.configure("none", "quick_settings")
        for (button in listOf(GamepadEvent.PrevSection, GamepadEvent.PrevTrigger, GamepadEvent.NextTrigger)) {
            events.clear()
            press(GamepadEvent.Select)
            assertFalse(press(button))
            assertFalse(release(button))
            release(GamepadEvent.Select)
            assertEquals(listOf(GamepadEvent.Select), events)
        }
    }

    @Test
    fun disabledShortcutsKeepImmediateSelect() {
        handler.configure("none", "none")
        assertFalse(press(GamepadEvent.Select))
    }

    @Test
    fun resettingAfterInputCaptureDropsPendingSelect() {
        press(GamepadEvent.Select)
        handler.reset()
        release(GamepadEvent.Select)
        assertTrue(events.isEmpty())
    }

    @Test
    fun shoulderRepeatsStayConsumedAfterSelectIsReleased() {
        press(GamepadEvent.Select)
        press(GamepadEvent.NextSection)
        release(GamepadEvent.Select)
        assertTrue(press(GamepadEvent.NextSection, repeat = 1))
        assertTrue(release(GamepadEvent.NextSection))
        assertFalse(press(GamepadEvent.NextSection))
        assertEquals(listOf(GamepadEvent.RightStickClick), events)
    }

    @Test
    fun unrelatedPreferenceEmissionDoesNotDropAHeldSelect() {
        press(GamepadEvent.Select)
        handler.configure("quick_menu", "quick_settings")
        release(GamepadEvent.Select)
        assertEquals(listOf(GamepadEvent.Select), events)
    }
}
