package com.nendo.argosy.data.sync.platform

import android.content.Context
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.repository.SaveSyncApiClient
import com.nendo.argosy.data.storage.AndroidDataAccessor
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.data.sync.fixtures.realFsFal
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class WiiFolderHandlerTest {
    private lateinit var tempDir: File
    private lateinit var handler: FolderSaveHandler
    private lateinit var fal: FileAccessLayer
    private lateinit var client: SaveSyncApiClient

    @Before
    fun setUp() {
        tempDir = createTempDirectory("wii_restore").toFile()
        val context = mockk<Context>(relaxed = true)
        every { context.cacheDir } returns File(tempDir, "cache").apply { mkdirs() }
        fal = realFsFal()
        val archiver = SaveArchiver(mockk<AndroidDataAccessor>(relaxed = true), fal)
        val registry = PlatformSaveHandlerRegistry(
            context, fal, archiver, mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true)
        )
        handler = registry.getFolderHandler("wii")!!
        client = SaveSyncApiClient(
            saveSyncDao = mockk(relaxed = true), emulatorConfigDao = mockk(relaxed = true),
            emulatorResolver = mockk(relaxed = true), builtinCoreResolver = mockk(relaxed = true),
            gameDao = mockk(relaxed = true), savePathResolver = mockk(relaxed = true),
            userPreferencesRepository = mockk(relaxed = true), fal = fal,
            switchSaveHandler = mockk(relaxed = true), gciSaveHandler = mockk(relaxed = true),
            saveHandlerRegistry = registry, conflictDetector = mockk(relaxed = true),
            saveUploader = { mockk(relaxed = true) }, saveDownloader = { mockk(relaxed = true) },
            syncPreferencesRepository = mockk(relaxed = true), snapshotRouter = { mockk(relaxed = true) }
        )
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun base(name: String) = File(tempDir, "$name/User/Wii/title/00010000")

    @Test
    fun `split save ids find only their title in an existing NAND`() {
        val base = base("builtin").apply { mkdirs() }
        val title = File(base, "524d4745").apply { mkdirs() }
        File(base, "524d4750").mkdirs()

        assertEquals(title.path, handler.findSaveFolderBySaveId(base.path, SAVE_ID))
        assertEquals(listOf(title.path), handler.findAllSaveFoldersBySaveId(base.path, SAVE_ID))
        assertEquals(listOf(title.path), handler.findAllSaveFoldersBySaveId(base.path, "524D4745"))
    }

    @Test
    fun `construction keeps one category and lowercase title without creating directories`() {
        val base = base("builtin")

        assertEquals(File(base, "524d4745").path, handler.constructSavePath(base.path, SAVE_ID))
        assertEquals(File(base, "524d4745").path, handler.constructSavePath(base.path, "524D4745"))
        assertFalse(base.exists())
    }

    @Test
    fun `split categories address separate titles even when their low ids coincide`() {
        val disc = base("builtin").apply { mkdirs() }
        val channel = File(disc.parentFile, "00010001").apply { mkdirs() }
        val discSave = File(disc, "524d4745").apply { mkdirs() }
        val channelSave = File(channel, "524d4745").apply { mkdirs() }

        assertEquals(channelSave.path, handler.constructSavePath(disc.path, "00010001/524D4745"))
        assertEquals(listOf(channelSave.path), handler.findAllSaveFoldersBySaveId(disc.path, "00010001/524d4745"))
        assertEquals(listOf(discSave.path), handler.findAllSaveFoldersBySaveId(disc.path, SAVE_ID))
    }

    @Test
    fun `missing titles are absent with missing or occupied parents`() {
        val base = base("builtin")
        assertNull(handler.findSaveFolderBySaveId(base.path, SAVE_ID))
        File(base, "524d4750").mkdirs()
        assertNull(handler.findSaveFolderBySaveId(base.path, SAVE_ID))
        assertTrue(handler.findAllSaveFoldersBySaveId(base.path, SAVE_ID).isEmpty())
    }

    @Test
    fun `malformed identifiers cannot construct or match a save path or archive`() {
        val base = base("builtin").apply { mkdirs() }
        File(base, "524d4745").mkdirs()
        for (id in listOf("", "RMGE", "../524d4745", "00010000/../524d4745", "00010000/524d4745/extra", "00010000\\524d4745")) {
            assertNull(id, handler.constructSavePath(base.path, id))
            assertTrue(id, handler.findAllSaveFoldersBySaveId(base.path, id).isEmpty())
            assertNull(id, handler.matchArchiveRoot("524d4745", id))
        }
    }

    @Test
    fun `standalone archives restore to built-in NAND and preserve other titles`() = runTest {
        val source = File(base("standalone"), "524d4745/data").apply { mkdirs() }
        val bytes = byteArrayOf(4, 5, 6)
        File(source, "GameData.bin").writeBytes(bytes)
        val destinationBase = base("builtin").apply { mkdirs() }
        val other = File(destinationBase, "524d4750/data/other.bin").apply {
            parentFile.mkdirs()
            writeBytes(byteArrayOf(7))
        }
        val destination = handler.constructSavePath(destinationBase.path, SAVE_ID)!!
        val context = SaveContext(
            config = SavePathRegistry.getConfigForPlatform("argosy", "wii")!!,
            romPath = null, saveId = SAVE_ID, emulatorPackage = null, gameId = 9L,
            gameTitle = "Super Mario Galaxy", platformSlug = "wii", emulatorId = "argosy",
            localSavePath = destination
        )
        val archive = handler.prepareForUpload(source.parent, context)!!

        assertEquals(FolderSaveHandler.ArchiveRootMatch.EXACT, handler.matchArchive(archive.file, SAVE_ID))
        assertTrue(client.clearSavesBeforeRestore(destination, "wii", SAVE_ID, setOf("524d4745")))
        assertTrue(handler.extractDownload(archive.file, context).success)
        assertArrayEquals(bytes, File(destination, "data/GameData.bin").readBytes())
        assertArrayEquals(byteArrayOf(7), other.readBytes())
    }

    @Test
    fun `clearing a title removes its old files but preserves unrelated titles`() = runTest {
        val base = base("builtin")
        val own = File(base, "524d4745/data/old.bin").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(1)) }
        val other = File(base, "524d4750/data/other.bin").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(2)) }

        assertTrue(client.clearSavesBeforeRestore(own.parentFile.parent, "wii", SAVE_ID, setOf("524d4745")))
        assertFalse(own.exists())
        assertArrayEquals(byteArrayOf(2), other.readBytes())
    }

    @Test
    fun `a foreign or unread archive cannot clear an existing title`() = runTest {
        val own = File(base("builtin"), "524d4745/data/GameData.bin").apply {
            parentFile.mkdirs()
            writeBytes(byteArrayOf(4, 2))
        }

        for (roots in listOf(null, emptySet(), setOf("524d4750"), setOf("524d4745", "524d4750"))) {
            assertTrue(client.clearSavesBeforeRestore(own.parentFile.parent, "wii", SAVE_ID, roots))
            assertArrayEquals(byteArrayOf(4, 2), own.readBytes())
        }
    }

    @Test
    fun `failed deletion is reported without removing another title`() = runTest {
        val base = base("builtin")
        val own = File(base, "524d4745/data/old.bin").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(1)) }
        val other = File(base, "524d4750/data/other.bin").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(2)) }
        every { fal.deleteRecursively(own.parentFile.parent) } returns false

        assertFalse(client.clearSavesBeforeRestore(own.parentFile.parent, "wii", SAVE_ID, setOf("524d4745")))
        assertTrue(own.exists())
        assertTrue(other.exists())
        assertFalse(client.clearSavesBeforeRestore(base.path, "wii", null, emptySet()))
    }

    private companion object {
        const val SAVE_ID = "00010000/524d4745"
    }
}
