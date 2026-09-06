package com.nendo.argosy.ui.screens.syncmonitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.remote.romm.PlatformSyncState
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.sync.PlatformSyncQueue
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.core.input.SoundType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SyncMonitorViewModel @Inject constructor(
    private val romMRepository: RomMRepository,
    private val platformSyncQueue: PlatformSyncQueue,
    private val syncPreferencesRepository: SyncPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SyncMonitorUiState())
    val uiState: StateFlow<SyncMonitorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            romMRepository.syncProgress.collect { progress ->
                _uiState.update { state ->
                    val rows = progress.platforms
                    val active = rows.indexOfFirst { it.state == PlatformSyncState.SYNCING }
                    state.copy(
                        isSyncing = progress.isSyncing,
                        rows = rows.ifEmpty { state.rows },
                        focusedIndex = resolveFocus(state, rows, active)
                    )
                }
            }
        }
        viewModelScope.launch {
            syncPreferencesRepository.preferences.collect { prefs ->
                _uiState.update { it.copy(lastSyncedAt = prefs.lastRommSync) }
            }
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isConnected = romMRepository.isConnected()) }
        }
    }

    /**
     * Following moves focus with the sync; a user who has taken over keeps their row unless it
     * has gone out of range under them.
     */
    private fun resolveFocus(
        state: SyncMonitorUiState,
        rows: List<com.nendo.argosy.data.remote.romm.PlatformSyncRow>,
        activeIndex: Int
    ): Int = when {
        rows.isEmpty() -> state.focusedIndex
        state.followActive && activeIndex >= 0 -> activeIndex
        else -> state.focusedIndex.coerceIn(0, rows.lastIndex)
    }

    private fun moveFocus(delta: Int): Boolean {
        var moved = false
        _uiState.update { state ->
            if (state.rows.isEmpty()) return@update state
            val next = (state.focusedIndex + delta).coerceIn(0, state.rows.lastIndex)
            moved = next != state.focusedIndex
            state.copy(
                focusedIndex = next,
                followActive = next == state.activeIndex
            )
        }
        return moved
    }

    fun focusRow(index: Int) {
        _uiState.update { state ->
            if (index !in state.rows.indices) return@update state
            state.copy(focusedIndex = index, followActive = index == state.activeIndex)
        }
    }

    fun syncNow() {
        if (_uiState.value.isSyncing) return
        platformSyncQueue.enqueueLibrary()
        _uiState.update { it.copy(followActive = true) }
    }

    fun createInputHandler(onBack: () -> Unit): InputHandler = object : InputHandler {
        override fun onUp(): InputResult =
            if (moveFocus(-1)) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)

        override fun onDown(): InputResult =
            if (moveFocus(1)) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)

        override fun onSecondaryAction(): InputResult {
            if (_uiState.value.isSyncing || !_uiState.value.isConnected) return InputResult.UNHANDLED
            syncNow()
            return InputResult.HANDLED
        }

        override fun onBack(): InputResult {
            onBack()
            return InputResult.HANDLED
        }
    }
}
