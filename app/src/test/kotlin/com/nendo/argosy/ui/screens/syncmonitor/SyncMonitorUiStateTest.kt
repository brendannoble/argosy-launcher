package com.nendo.argosy.ui.screens.syncmonitor

import com.nendo.argosy.data.remote.romm.PlatformSyncRow
import com.nendo.argosy.data.remote.romm.PlatformSyncState
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncMonitorUiStateTest {

    private fun row(
        id: Long,
        state: PlatformSyncState,
        done: Int = 0,
        total: Int = 0
    ) = PlatformSyncRow(
        platformId = id,
        name = "p$id",
        slug = "p$id",
        state = state,
        gamesDone = done,
        gamesTotal = total
    )

    @Test
    fun `active index points at the syncing row`() {
        val state = SyncMonitorUiState(
            rows = listOf(
                row(1, PlatformSyncState.DONE),
                row(2, PlatformSyncState.SYNCING),
                row(3, PlatformSyncState.QUEUED)
            )
        )
        assertEquals(1, state.activeIndex)
    }

    @Test
    fun `a resumed platform counts as finished for the header`() {
        val state = SyncMonitorUiState(
            rows = listOf(
                row(1, PlatformSyncState.ALREADY_SYNCED),
                row(2, PlatformSyncState.FAILED),
                row(3, PlatformSyncState.QUEUED)
            )
        )
        assertEquals(2, state.platformsDone)
    }

    @Test
    fun `pass fraction counts the active platform's own progress`() {
        val state = SyncMonitorUiState(
            rows = listOf(
                row(1, PlatformSyncState.DONE),
                row(2, PlatformSyncState.SYNCING, done = 50, total = 100),
                row(3, PlatformSyncState.QUEUED),
                row(4, PlatformSyncState.QUEUED)
            )
        )
        assertEquals(0.375f, state.passFraction, 0.0001f)
    }

    @Test
    fun `pass fraction is zero with no rows and never leaves the unit range`() {
        assertEquals(0f, SyncMonitorUiState().passFraction, 0.0001f)

        val overrun = SyncMonitorUiState(
            rows = listOf(row(1, PlatformSyncState.SYNCING, done = 500, total = 100))
        )
        assertEquals(1f, overrun.passFraction, 0.0001f)
    }

    @Test
    fun `a platform reporting no total contributes nothing rather than dividing by zero`() {
        val state = SyncMonitorUiState(
            rows = listOf(
                row(1, PlatformSyncState.DONE),
                row(2, PlatformSyncState.SYNCING, done = 7, total = 0)
            )
        )
        assertEquals(0.5f, state.passFraction, 0.0001f)
    }
}
