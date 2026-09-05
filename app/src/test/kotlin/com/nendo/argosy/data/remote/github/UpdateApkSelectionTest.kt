package com.nendo.argosy.data.remote.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateApkSelectionTest {

    private fun asset(name: String) = GitHubAsset(name, "https://example/$name", 1L)

    private val release = listOf(
        asset("argosy-v2.13.0.apk"),
        asset("argosy-v2.13.0-arm64.apk"),
        asset("argosy-v2.13.0-arm32.apk"),
        asset("argosy-v2.13.0-x86_64.apk"),
        asset("argosy-v2.13.0-x86.apk")
    )

    private fun pick(versionCode: Int) =
        UpdateRepository.selectApkAsset(release, versionCode)?.name

    @Test
    fun `each abi takes its own asset`() {
        assertEquals("argosy-v2.13.0-arm32.apk", pick(1_000_332))
        assertEquals("argosy-v2.13.0-arm64.apk", pick(2_000_332))
        assertEquals("argosy-v2.13.0-x86.apk", pick(4_000_332))
        assertEquals("argosy-v2.13.0-x86_64.apk", pick(5_000_332))
    }

    @Test
    fun `x86 does not take the x86_64 asset whose name contains it`() {
        assertEquals("argosy-v2.13.0-x86.apk", pick(4_000_332))
    }

    @Test
    fun `universal build takes the unsuffixed asset`() {
        assertEquals("argosy-v2.13.0.apk", pick(3_000_332))
    }

    @Test
    fun `an abi with no asset of its own falls back to the unsuffixed one`() {
        val without = release.filterNot { it.name.endsWith("-x86_64.apk") }
        assertEquals(
            "argosy-v2.13.0.apk",
            UpdateRepository.selectApkAsset(without, 5_000_332)?.name
        )
    }

    @Test
    fun `a release of only suffixed assets still yields one`() {
        val suffixedOnly = release.filterNot { it.name == "argosy-v2.13.0.apk" }
        assertEquals(
            "argosy-v2.13.0-arm64.apk",
            UpdateRepository.selectApkAsset(suffixedOnly, 3_000_332)?.name
        )
    }

    @Test
    fun `non apk assets are never chosen`() {
        assertNull(UpdateRepository.selectApkAsset(listOf(asset("notes.md")), 2_000_332))
    }
}
