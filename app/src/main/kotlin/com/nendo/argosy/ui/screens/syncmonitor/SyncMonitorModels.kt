package com.nendo.argosy.ui.screens.syncmonitor

import com.nendo.argosy.data.remote.romm.PlatformSyncRow
import java.time.Instant

/**
 * [followActive] tracks the syncing row on its own until the user moves, at which point their
 * choice of row outranks it. Landing back on the active row hands following back, so the mode is
 * always something the user can see rather than something a timer decides.
 */
data class SyncMonitorUiState(
    val isSyncing: Boolean = false,
    val isConnected: Boolean = true,
    val rows: List<PlatformSyncRow> = emptyList(),
    val focusedIndex: Int = 0,
    val followActive: Boolean = true,
    val lastSyncedAt: Instant? = null,
    val passErrors: List<String> = emptyList()
) {
    val activeIndex: Int
        get() = rows.indexOfFirst { it.state == com.nendo.argosy.data.remote.romm.PlatformSyncState.SYNCING }

    val focusedRow: PlatformSyncRow?
        get() = rows.getOrNull(focusedIndex)

    val hasRows: Boolean get() = rows.isNotEmpty()

    val platformsDone: Int
        get() = rows.count {
            it.state == com.nendo.argosy.data.remote.romm.PlatformSyncState.DONE ||
                it.state == com.nendo.argosy.data.remote.romm.PlatformSyncState.ALREADY_SYNCED ||
                it.state == com.nendo.argosy.data.remote.romm.PlatformSyncState.FAILED
        }

    /**
     * Whole-pass fraction, counting the active platform's own progress so the header advances
     * between platforms rather than only when one finishes.
     */
    val passFraction: Float
        get() {
            if (rows.isEmpty()) return 0f
            val active = rows.getOrNull(activeIndex)
            val activeShare = if (active != null && active.gamesTotal > 0) {
                active.gamesDone.toFloat() / active.gamesTotal
            } else {
                0f
            }
            return ((platformsDone + activeShare) / rows.size).coerceIn(0f, 1f)
        }
}
