package com.nendo.argosy.ui.screens.syncmonitor

import com.nendo.argosy.data.remote.romm.PlatformSyncState
import java.time.Instant

/**
 * One platform as this screen shows it: what the library holds for it, and what a running sync is
 * doing to it. The counts are local facts and stay readable with no server and no sync.
 */
data class SyncMonitorRow(
    val platformId: Long,
    val name: String,
    val slug: String,
    val syncEnabled: Boolean,
    val games: Int = 0,
    val downloaded: Int = 0,
    val withSaves: Int = 0,
    val state: PlatformSyncState = PlatformSyncState.IDLE,
    val gamesDone: Int = 0,
    val gamesTotal: Int = 0,
    val added: Int = 0,
    val updated: Int = 0,
    val removed: Int = 0,
    val error: String? = null
)

/**
 * [followActive] tracks the syncing row on its own until the user moves, at which point their
 * choice of row outranks it. Landing back on the active row hands following back, so the mode is
 * always something the user can see rather than something a timer decides.
 */
data class SyncMonitorUiState(
    val isSyncing: Boolean = false,
    val isConnected: Boolean = true,
    val libraryBusy: Boolean = false,
    /**
     * A pass is running somewhere, queue or not. The sync service holds one mutex for every kind
     * of sync, so a second request would be refused there rather than run; this is what the row
     * actions gate on so they never accept a press that would silently do nothing.
     */
    val syncRunning: Boolean = false,
    val busyPlatformIds: Set<Long> = emptySet(),
    val enabledRows: List<SyncMonitorRow> = emptyList(),
    val disabledRows: List<SyncMonitorRow> = emptyList(),
    val focusedIndex: Int = 0,
    val followActive: Boolean = true,
    val lastSyncedAt: Instant? = null
) {
    val rows: List<SyncMonitorRow> get() = enabledRows + disabledRows

    val activeIndex: Int
        get() = rows.indexOfFirst { it.state == PlatformSyncState.SYNCING }

    val focusedRow: SyncMonitorRow? get() = rows.getOrNull(focusedIndex)

    val hasRows: Boolean get() = rows.isNotEmpty()

    val totalGames: Int get() = rows.sumOf { it.games }

    val totalDownloaded: Int get() = rows.sumOf { it.downloaded }

    val platformsDone: Int
        get() = enabledRows.count {
            it.state == PlatformSyncState.DONE ||
                it.state == PlatformSyncState.ALREADY_SYNCED ||
                it.state == PlatformSyncState.FAILED
        }

    val failedCount: Int get() = enabledRows.count { it.state == PlatformSyncState.FAILED }

    fun canSyncRow(row: SyncMonitorRow): Boolean =
        isConnected &&
            row.syncEnabled &&
            !libraryBusy &&
            !syncRunning &&
            row.platformId !in busyPlatformIds

    /**
     * Whether the focused row can be synced on its own right now.
     */
    val canSyncFocused: Boolean
        get() {
            val row = focusedRow ?: return false
            return isConnected &&
                row.syncEnabled &&
                !libraryBusy &&
                !syncRunning &&
                row.platformId !in busyPlatformIds
        }

    val canSyncAll: Boolean get() = isConnected && !libraryBusy && !syncRunning

    /**
     * Whole-pass fraction, counting the active platform's own progress so the header advances
     * between platforms rather than only when one finishes.
     */
    val passFraction: Float
        get() {
            if (enabledRows.isEmpty()) return 0f
            val active = rows.getOrNull(activeIndex)
            val activeShare = if (active != null && active.gamesTotal > 0) {
                active.gamesDone.toFloat() / active.gamesTotal
            } else {
                0f
            }
            return ((platformsDone + activeShare) / enabledRows.size).coerceIn(0f, 1f)
        }
}
