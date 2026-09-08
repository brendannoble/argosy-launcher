package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.RomMAccountRepository
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class RomMServerUrlTest {
    private val oldUrl = "http://192.168.1.100:7676/"
    private val newUrl = "https://romm.example.com/"
    private val preferences = mockk<UserPreferencesRepository>()
    private val accounts = mockk<RomMAccountRepository>(relaxed = true)
    private val factory = mockk<RomMApiFactory>()
    private val oldApi = mockk<RomMApi>()
    private val newApi = mockk<RomMApi>()
    private val heartbeat = Response.success(RomMHeartbeatResponse())
    private val user = RomMUser(7, "player", true, "viewer")
    private val manager = RomMConnectionManager(
        mockk(relaxed = true), preferences, Lazy { mockk(relaxed = true) },
        mockk(relaxed = true), Lazy { accounts }, Lazy { mockk(relaxed = true) },
        Lazy { mockk(relaxed = true) }, Lazy { mockk(relaxed = true) }, factory
    )

    init {
        every { preferences.preferences } returns flowOf(
            UserPreferences(rommBaseUrl = oldUrl, rommToken = "saved-token", rommUserId = user.id, rommDeviceId = "existing-device")
        )
        every { factory.create(oldUrl, any()) } returns oldApi
        every { factory.create(newUrl, any()) } returns newApi
        coEvery { oldApi.heartbeat() } returns heartbeat
        coEvery { oldApi.getCurrentUser() } returns Response.success(user)
        coEvery { newApi.heartbeat() } returns heartbeat
        coEvery { newApi.getCurrentUser() } returns Response.success(user)
    }

    @Test
    fun `valid address replaces the connection using saved authentication`() = runTest {
        manager.connect(oldUrl, "saved-token")

        assertTrue(manager.updateServerUrl("  https://romm.example.com  ") is RomMResult.Success)

        assertEquals(newUrl, manager.getBaseUrl())
        assertSame(newApi, manager.getApi())
        coVerify { accounts.updateServerUrl(oldUrl, newUrl, "saved-token") }
    }

    @Test
    fun `rejected credentials preserve the old connection and stored address`() = runTest {
        manager.connect(oldUrl, "saved-token")
        val previousState = manager.connectionState.value
        coEvery { newApi.getCurrentUser() } returns Response.error(401, "".toResponseBody())

        val result = manager.updateServerUrl(newUrl) as RomMResult.Error

        assertEquals(401, result.code)
        assertEquals(oldUrl, manager.getBaseUrl())
        assertSame(oldApi, manager.getApi())
        assertEquals(previousState, manager.connectionState.value)
        coVerify(exactly = 0) { accounts.updateServerUrl(any(), any(), any()) }
    }

    @Test
    fun `unreachable address preserves the old connection`() = runTest {
        manager.connect(oldUrl, "saved-token")
        coEvery { newApi.heartbeat() } throws java.io.IOException("unreachable")

        assertTrue(manager.updateServerUrl(newUrl) is RomMResult.Error)

        assertEquals(oldUrl, manager.getBaseUrl())
        assertSame(oldApi, manager.getApi())
        coVerify(exactly = 0) { accounts.updateServerUrl(any(), any(), any()) }
    }

    @Test
    fun `different user cannot replace the existing library connection`() = runTest {
        manager.connect(oldUrl, "saved-token")
        coEvery { newApi.getCurrentUser() } returns Response.success(user.copy(id = 8))

        assertTrue(manager.updateServerUrl(newUrl) is RomMResult.Error)

        assertSame(oldApi, manager.getApi())
        coVerify(exactly = 0) { accounts.updateServerUrl(any(), any(), any()) }
    }

    @Test
    fun `fresh pairing can move the address when the registered device still exists`() = runTest {
        coEvery { newApi.getDevices() } returns Response.success(
            listOf(RomMDevice("existing-device", null, null, null, null))
        )

        assertTrue(manager.connectWithToken(newUrl, "fresh-token") is RomMResult.Success)

        assertEquals(newUrl, manager.getBaseUrl())
        coVerify { accounts.updateServerUrl(oldUrl, newUrl, "fresh-token") }
    }

    @Test
    fun `fresh pairing at a different instance cannot move the address`() = runTest {
        manager.connect(oldUrl, "saved-token")
        coEvery { newApi.getDevices() } returns Response.success(emptyList())

        assertTrue(manager.connectWithToken(newUrl, "fresh-token") is RomMResult.Error)

        assertSame(oldApi, manager.getApi())
        coVerify(exactly = 0) { accounts.updateServerUrl(any(), any(), any()) }
    }
}
