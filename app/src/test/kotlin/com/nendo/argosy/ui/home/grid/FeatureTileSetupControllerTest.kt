package com.nendo.argosy.ui.home.grid

import com.nendo.argosy.domain.model.FeatureTileKind
import com.nendo.argosy.domain.model.HomeTile
import com.nendo.argosy.domain.model.HomeTileTargetRef
import com.nendo.argosy.domain.model.TileRect
import com.nendo.argosy.ui.components.CustomGridState
import com.nendo.argosy.ui.components.FeatureFilterOptions
import com.nendo.argosy.ui.components.FeatureTileSetup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TRACKED_GAME_ID = 42L

class FeatureTileSetupControllerTest {

    private var state = CustomGridState()
    private val placed = mutableListOf<HomeTileTargetRef.Feature>()
    private val edited = mutableListOf<Pair<Long, HomeTileTargetRef.Feature>>()
    private var trackRequests = 0

    private fun controller(scope: CoroutineScope) = FeatureTileSetupController(
        scope = scope,
        filterOptions = { FeatureFilterOptions(platforms = emptyList(), genres = emptyList()) },
        read = { state },
        write = { transform -> state = transform(state) },
        onPlace = { target -> placed += target },
        onEdit = { tileId, target -> edited += tileId to target },
        onTrackGame = { trackRequests += 1 }
    )

    @Test
    fun `an RA tile settles on the game the picker named`() = runTest {
        val controller = controller(this)

        controller.begin(kind = FeatureTileKind.RA_SUMMARY)
        controller.confirm(FeatureTileSetup.ROW_MODE_TRACK)
        controller.trackGame(TRACKED_GAME_ID)

        assertEquals(1, trackRequests)
        assertEquals(
            HomeTileTargetRef.Feature(
                kind = FeatureTileKind.RA_SUMMARY,
                pickedGameId = TRACKED_GAME_ID
            ),
            placed.single()
        )
        assertNull(state.featureSetup)
    }

    @Test
    fun `the account row settles an RA tile on no game`() = runTest {
        val controller = controller(this)

        controller.begin(kind = FeatureTileKind.RA_SUMMARY)
        controller.confirm(FeatureTileSetup.ROW_MODE_ACCOUNT)

        assertEquals(0, trackRequests)
        assertEquals(
            HomeTileTargetRef.Feature(kind = FeatureTileKind.RA_SUMMARY, pickedGameId = null),
            placed.single()
        )
        assertNull(state.featureSetup)
    }

    @Test
    fun `a random tile settles with the filters it was answered with`() = runTest {
        val controller = controller(this)

        controller.begin(kind = FeatureTileKind.RANDOM_GAME)
        advanceUntilIdle()
        controller.confirm(FeatureTileSetup.ROW_DOWNLOADED_ONLY)
        controller.confirm(FeatureTileSetup.ROW_NEVER_PLAYED)
        controller.confirm(FeatureTileSetup.ROW_DONE)

        val target = placed.single()
        assertEquals(FeatureTileKind.RANDOM_GAME, target.kind)
        assertNull(target.pickedGameId)
        assertTrue(target.filters.neverPlayed)
        assertFalse(target.filters.downloadedOnly)
        assertNull(state.featureSetup)
    }

    @Test
    fun `editing an RA tile changes that tile rather than placing another`() = runTest {
        val controller = controller(this)
        val tile = HomeTile(
            id = 7L,
            pageIndex = 0,
            rect = TileRect(0, 0),
            target = HomeTileTargetRef.Feature(FeatureTileKind.RA_SUMMARY)
        )

        controller.begin(existing = tile)
        controller.confirm(FeatureTileSetup.ROW_MODE_ACCOUNT)

        assertTrue(placed.isEmpty())
        assertEquals(
            7L to HomeTileTargetRef.Feature(
                kind = FeatureTileKind.RA_SUMMARY,
                pickedGameId = null
            ),
            edited.single()
        )
    }
}
