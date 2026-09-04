package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.HomeTileDao
import com.nendo.argosy.data.local.entity.HomeTileEntity
import com.nendo.argosy.domain.model.FeatureTileKind
import com.nendo.argosy.domain.model.HomeTileTargetRef
import com.nendo.argosy.domain.model.TileRect
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val OWNER = 1L
private const val TILE_ID = 7L
private const val KEY_PICKED_GAME_ID = "pickedGameId"

class HomeTileRepositoryTest {

    private val dao = mockk<HomeTileDao>(relaxed = true)
    private val repository = HomeTileRepository(dao)

    private suspend fun roundTrip(
        target: HomeTileTargetRef.Feature
    ): Pair<HomeTileEntity, HomeTileTargetRef> {
        val written = slot<HomeTileEntity>()
        coEvery { dao.insert(capture(written)) } returns TILE_ID
        repository.place(ownerUserId = OWNER, pageIndex = 0, rect = TileRect(0, 0), target = target)
        val row = written.captured.copy(id = TILE_ID)
        every { dao.observeTiles(OWNER) } returns flowOf(listOf(row))
        every { dao.observeAllEpisodes() } returns flowOf(emptyList())
        return row to repository.observeTiles(OWNER).first().single().target
    }

    @Test
    fun `an RA tile keeps its tracked game across a write and a read`() = runTest {
        val target = HomeTileTargetRef.Feature(FeatureTileKind.RA_SUMMARY, pickedGameId = 42L)

        val (row, read) = roundTrip(target)

        assertEquals(FeatureTileKind.RA_SUMMARY.name, row.featureKind)
        assertEquals(42L, JSONObject(row.featureConfig.orEmpty()).getLong(KEY_PICKED_GAME_ID))
        assertEquals(target, read)
    }

    @Test
    fun `an RA tile with no tracked game stores no pick and reads back null`() = runTest {
        val target = HomeTileTargetRef.Feature(FeatureTileKind.RA_SUMMARY)

        val (row, read) = roundTrip(target)

        assertFalse(JSONObject(row.featureConfig.orEmpty()).has(KEY_PICKED_GAME_ID))
        assertTrue(read is HomeTileTargetRef.Feature)
        assertEquals(null, (read as HomeTileTargetRef.Feature).pickedGameId)
        assertEquals(target, read)
    }
}
