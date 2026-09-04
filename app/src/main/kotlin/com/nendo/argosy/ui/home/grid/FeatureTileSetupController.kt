package com.nendo.argosy.ui.home.grid

import com.nendo.argosy.domain.model.FeatureTileKind
import com.nendo.argosy.domain.model.HomeTile
import com.nendo.argosy.domain.model.HomeTileTargetRef
import com.nendo.argosy.domain.model.RandomTileFilters
import com.nendo.argosy.ui.components.CustomGridState
import com.nendo.argosy.ui.components.FeatureFilterOptions
import com.nendo.argosy.ui.components.FeatureSetupStep
import com.nendo.argosy.ui.components.FeatureTileSetup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Drives the feature tiles' setup questions: the random game tile's filters and the
 * RetroAchievements tile's mode. Owns no state of its own: the run lives on
 * [CustomGridState.featureSetup] so the gamepad and touch reach it through the same reads and
 * writes as the rest of the grid.
 *
 * [onTrackGame] hands the screen to a game picker while the setup waits underneath; the pick comes
 * back through [trackGame], so the setup settles it the same way it settles every other answer.
 */
class FeatureTileSetupController(
    private val scope: CoroutineScope,
    private val filterOptions: suspend () -> FeatureFilterOptions,
    private val read: () -> CustomGridState,
    private val write: ((CustomGridState) -> CustomGridState) -> Unit,
    private val onPlace: (HomeTileTargetRef.Feature) -> Unit,
    private val onEdit: (Long, HomeTileTargetRef.Feature) -> Unit,
    private val onTrackGame: () -> Unit = {}
) {
    private val setup: FeatureTileSetup? get() = read().featureSetup

    fun begin(existing: HomeTile? = null, kind: FeatureTileKind = FeatureTileKind.RANDOM_GAME) {
        val target = existing?.target as? HomeTileTargetRef.Feature
        val effectiveKind = target?.kind ?: kind
        val current = target?.filters ?: RandomTileFilters()
        if (effectiveKind == FeatureTileKind.RA_SUMMARY) {
            write {
                it.copy(
                    featureSetup = FeatureTileSetup(
                        kind = effectiveKind,
                        editingTileId = existing?.id,
                        filters = current,
                        platforms = emptyList(),
                        genres = emptyList(),
                        pickedGameId = target?.pickedGameId,
                        focusIndex = if (target?.pickedGameId != null) {
                            FeatureTileSetup.ROW_MODE_TRACK
                        } else {
                            FeatureTileSetup.ROW_MODE_ACCOUNT
                        }
                    )
                )
            }
            return
        }
        scope.launch {
            val options = filterOptions()
            write {
                it.copy(
                    featureSetup = FeatureTileSetup(
                        kind = effectiveKind,
                        editingTileId = existing?.id,
                        filters = current,
                        platforms = options.platforms,
                        genres = options.genres,
                        pickedGameId = target?.pickedGameId
                    )
                )
            }
        }
    }

    fun close() = write { it.copy(featureSetup = null) }

    fun moveFocus(delta: Int) {
        val current = setup ?: return
        val count = current.rowCount
        if (count == 0) return
        update { it.copy(focusIndex = (it.focusIndex + delta).mod(count)) }
    }

    fun confirm(index: Int? = null) {
        val current = setup ?: return
        val row = index ?: current.focusIndex
        when (current.step) {
            FeatureSetupStep.MODE -> confirmModeRow(row)
            FeatureSetupStep.FILTERS -> confirmFilterRow(row)
            FeatureSetupStep.PLATFORMS -> current.platforms.getOrNull(row)?.let { option ->
                updateFilters { filters ->
                    filters.copy(platformIds = filters.platformIds.toggled(option.id))
                }
            }
            FeatureSetupStep.GENRES -> current.genres.getOrNull(row)?.let { genre ->
                updateFilters { filters -> filters.copy(genres = filters.genres.toggled(genre)) }
            }
        }
        if (index != null && setup != null) update { it.copy(focusIndex = index) }
    }

    /**
     * Returns to the filter list from a sub-list, landing on the row that opened it. Answers
     * whether there was anything to go back to.
     */
    fun back(): Boolean {
        val current = setup ?: return false
        val returnRow = when (current.step) {
            FeatureSetupStep.MODE, FeatureSetupStep.FILTERS -> return false
            FeatureSetupStep.PLATFORMS -> FeatureTileSetup.ROW_PLATFORMS
            FeatureSetupStep.GENRES -> FeatureTileSetup.ROW_GENRES
        }
        update { it.copy(step = FeatureSetupStep.FILTERS, focusIndex = returnRow) }
        return true
    }

    /**
     * Settles the RetroAchievements tile on the game the picker named. The setup is still the one
     * that opened the picker, so the tile it was placing or editing is the one that changes.
     */
    fun trackGame(gameId: Long) {
        val current = setup ?: return
        if (current.kind != FeatureTileKind.RA_SUMMARY) return
        settle(HomeTileTargetRef.Feature(FeatureTileKind.RA_SUMMARY, pickedGameId = gameId))
    }

    private fun confirmModeRow(row: Int) {
        when (row) {
            FeatureTileSetup.ROW_MODE_ACCOUNT ->
                settle(HomeTileTargetRef.Feature(FeatureTileKind.RA_SUMMARY, pickedGameId = null))
            FeatureTileSetup.ROW_MODE_TRACK -> onTrackGame()
        }
    }

    private fun confirmFilterRow(row: Int) {
        when (row) {
            FeatureTileSetup.ROW_DOWNLOADED_ONLY ->
                updateFilters { it.copy(downloadedOnly = !it.downloadedOnly) }
            FeatureTileSetup.ROW_NEVER_PLAYED ->
                updateFilters { it.copy(neverPlayed = !it.neverPlayed) }
            FeatureTileSetup.ROW_PLATFORMS ->
                update { it.copy(step = FeatureSetupStep.PLATFORMS, focusIndex = 0) }
            FeatureTileSetup.ROW_GENRES ->
                update { it.copy(step = FeatureSetupStep.GENRES, focusIndex = 0) }
            FeatureTileSetup.ROW_DONE -> setup?.let { current ->
                settle(
                    HomeTileTargetRef.Feature(
                        kind = current.kind,
                        filters = current.filters,
                        pickedGameId = current.pickedGameId
                    )
                )
            }
        }
    }

    private fun settle(target: HomeTileTargetRef.Feature) {
        val current = setup ?: return
        val editing = current.editingTileId
        if (editing == null) onPlace(target) else onEdit(editing, target)
        close()
    }

    private fun update(transform: (FeatureTileSetup) -> FeatureTileSetup) =
        write { state -> state.copy(featureSetup = state.featureSetup?.let(transform)) }

    private fun updateFilters(transform: (RandomTileFilters) -> RandomTileFilters) =
        update { it.copy(filters = transform(it.filters)) }

    private fun <T> Set<T>.toggled(value: T): Set<T> =
        if (value in this) this - value else this + value
}
