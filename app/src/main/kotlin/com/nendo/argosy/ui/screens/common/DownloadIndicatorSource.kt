package com.nendo.argosy.ui.screens.common

import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.download.DownloadState
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.steam.SteamContentManager
import com.nendo.argosy.data.steam.SteamDownloadState
import com.nendo.argosy.ui.common.appId
import com.nendo.argosy.ui.common.toIndicator
import com.nendo.argosy.ui.screens.home.GameDownloadIndicator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What every game card draws over its cover while a transfer is in flight, keyed by game id.
 *
 * Shared rather than owned by one screen: a download is started from wherever the game is, and the
 * card that started it has to answer for it on Home, in the library and on the companion screen.
 */
@Singleton
class DownloadIndicatorSource @Inject constructor(
    private val downloadManager: DownloadManager,
    private val steamContentManager: SteamContentManager,
    private val gameRepository: GameRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val rommIndicators: Flow<Map<Long, GameDownloadIndicator>> =
        downloadManager.state.map { downloadState ->
            val indicators = mutableMapOf<Long, GameDownloadIndicator>()

            downloadState.activeDownloads.forEach { download ->
                indicators[download.gameId] = when (download.state) {
                    DownloadState.EXTRACTING,
                    DownloadState.MOVING -> GameDownloadIndicator(
                        isExtracting = true,
                        progress = download.extractionPercent
                    )
                    else -> GameDownloadIndicator(
                        isDownloading = true,
                        progress = download.progressPercent
                    )
                }
            }

            downloadState.queue.forEach { download ->
                if (download.gameId in indicators) return@forEach
                val indicator = download.state.toIndicator(
                    download.progressPercent, download.extractionPercent
                )
                if (indicator.isShown) {
                    indicators[download.gameId] = indicator
                }
            }

            indicators
        }

    private val steamIndicators: Flow<Map<Long, GameDownloadIndicator>> =
        steamContentManager.downloadState
            .map { steamIndicatorsOrUnchanged(it) }
            .scan(emptyMap<Long, GameDownloadIndicator>()) { previous, next -> next ?: previous }

    val indicators: StateFlow<Map<Long, GameDownloadIndicator>> =
        combine(rommIndicators, steamIndicators) { romm, steam -> romm + steam }
            .stateIn(scope, SharingStarted.WhileSubscribed(SUBSCRIPTION_GRACE_MS), emptyMap())

    private suspend fun steamIndicatorsOrUnchanged(
        steamState: SteamDownloadState
    ): Map<Long, GameDownloadIndicator>? {
        if (steamState is SteamDownloadState.Idle) return emptyMap()
        val appId = steamState.appId ?: return UNCHANGED
        val game = gameRepository.getBySteamAppId(appId) ?: return UNCHANGED
        val progress = steamContentManager.activeDownload.value?.progress
            ?: (steamState as? SteamDownloadState.Paused)?.progress
            ?: 0f
        val indicator = steamState.toIndicator(progress) ?: return emptyMap()
        return mapOf(game.id to indicator)
    }
}

private const val SUBSCRIPTION_GRACE_MS = 5_000L

private val UNCHANGED: Map<Long, GameDownloadIndicator>? = null
