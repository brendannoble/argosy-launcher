package com.nendo.argosy.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenDimmerStateTest {
    @Test
    fun aRecreatedDisplayUsesTheRemainingIdleTime() {
        var now = 100L
        val state = ScreenDimmerState { now }

        now += 40L
        assertEquals(60L, state.remainingTimeout(100L))
        now += 80L
        assertEquals(0L, state.remainingTimeout(100L))
    }

    @Test
    fun activityOnEitherDisplayRestartsTheSharedDeadline() {
        var now = 100L
        val state = ScreenDimmerState { now }
        val primary = state.lastActivityTime
        val companion = state.lastActivityTime

        now += 100L
        state.recordActivity()

        assertEquals(200L, primary.value)
        assertEquals(primary.value, companion.value)
        assertEquals(100L, state.remainingTimeout(100L))
    }
}
