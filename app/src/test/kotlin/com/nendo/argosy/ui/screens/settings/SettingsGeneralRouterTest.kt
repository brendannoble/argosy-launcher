package com.nendo.argosy.ui.screens.settings

import com.nendo.argosy.data.preferences.DisplayRoleOverride
import com.nendo.argosy.util.SecondaryDisplayType
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsGeneralRouterTest {
    @Test
    fun `display role carousel visits every option in both directions`() {
        val state = MutableStateFlow(SettingsUiState())
        val vm = mockk<SettingsViewModel>()
        every { vm._uiState } returns state
        every { vm.displayAffinityHelper.secondaryDisplayType } returns SecondaryDisplayType.EXTERNAL
        val selected = slot<DisplayRoleOverride>()
        mockkStatic(::routeSetDisplayRoleOverride)
        try {
            every { routeSetDisplayRoleOverride(vm, capture(selected)) } returns Unit
            val options = listOf(DisplayRoleOverride.AUTO, DisplayRoleOverride.STANDARD, DisplayRoleOverride.SWAPPED)
            for (direction in listOf(-1, 1)) {
                for ((index, current) in options.withIndex()) {
                    state.value = state.value.copy(display = state.value.display.copy(displayRoleOverride = current))
                    selected.clear()
                    routeCycleDisplayRoleOverride(vm, direction)
                    assertEquals("$current, direction $direction", options[(index + direction).mod(options.size)], selected.captured)
                }
            }
        } finally {
            unmockkStatic(::routeSetDisplayRoleOverride)
        }
    }
}
