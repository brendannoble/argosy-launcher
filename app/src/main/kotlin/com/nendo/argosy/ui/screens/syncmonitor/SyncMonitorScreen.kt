package com.nendo.argosy.ui.screens.syncmonitor

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nendo.argosy.R
import com.nendo.argosy.data.remote.romm.PlatformSyncRow
import com.nendo.argosy.data.remote.romm.PlatformSyncState
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.animateScrollToItemCentered
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.primitives.ArgosyProgressBar
import com.nendo.argosy.ui.primitives.ProgressBarStyle
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.MotionTokens
import com.nendo.argosy.ui.util.clickableNoFocus

@Composable
fun SyncMonitorScreen(
    onBack: () -> Unit,
    onDrawerToggle: () -> Unit,
    viewModel: SyncMonitorViewModel = hiltViewModel()
) {
    val inputDispatcher = LocalInputDispatcher.current
    val inputHandler = remember(onBack) { viewModel.createInputHandler(onBack = onBack) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, inputHandler) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_SYNC_MONITOR)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_SYNC_MONITOR)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val theme = LocalArgosyTheme.current

    LaunchedEffect(uiState.focusedIndex, uiState.rows.size) {
        if (uiState.hasRows) listState.animateScrollToItemCentered(uiState.focusedIndex)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(Dimens.spacingLg)) {
            SyncMonitorHeader(uiState)

            Spacer(Modifier.height(Dimens.spacingMd))

            if (!uiState.hasRows) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (uiState.isConnected) {
                            stringResource(R.string.syncmonitor_empty_idle)
                        } else {
                            stringResource(R.string.syncmonitor_empty_disconnected)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.textDim
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
                ) {
                    itemsIndexed(uiState.rows, key = { _, row -> row.platformId }) { index, row ->
                        PlatformRow(
                            row = row,
                            focused = index == uiState.focusedIndex,
                            onClick = { viewModel.focusRow(index) }
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            FooterHints(
                hints = buildHints(uiState),
                onHintClick = { button ->
                    when (button) {
                        InputButton.Y -> viewModel.syncNow()
                        InputButton.B -> onBack()
                        else -> Unit
                    }
                }
            )
        }
    }
}

@Composable
private fun SyncMonitorHeader(state: SyncMonitorUiState) {
    val theme = LocalArgosyTheme.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.syncmonitor_title),
            style = MaterialTheme.typography.titleLarge,
            color = theme.textPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(Dimens.spacingXs))
        Text(
            text = if (state.isSyncing) {
                stringResource(
                    R.string.syncmonitor_progress_platforms,
                    state.platformsDone,
                    state.rows.size
                )
            } else {
                stringResource(R.string.syncmonitor_idle_subtitle)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textDim
        )
        if (state.isSyncing) {
            Spacer(Modifier.height(Dimens.spacingSm))
            InterpolatedProgressBar(
                target = state.passFraction,
                style = ProgressBarStyle.Active,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PlatformRow(
    row: PlatformSyncRow,
    focused: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val background = if (focused) theme.focusAccent.copy(alpha = 0.15f) else theme.surfaceElevated

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .background(background)
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = row.name,
                style = MaterialTheme.typography.bodyLarge,
                color = theme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = rowStatusText(row),
                style = MaterialTheme.typography.labelMedium,
                color = if (row.state == PlatformSyncState.FAILED) theme.textMute else theme.textDim
            )
        }

        if (row.state == PlatformSyncState.SYNCING) {
            Spacer(Modifier.height(Dimens.spacingXs))
            InterpolatedProgressBar(
                target = if (row.gamesTotal > 0) {
                    row.gamesDone.toFloat() / row.gamesTotal
                } else {
                    0f
                },
                style = ProgressBarStyle.Active,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun rowStatusText(row: PlatformSyncRow): String = when (row.state) {
    PlatformSyncState.QUEUED -> stringResource(R.string.syncmonitor_state_queued)
    PlatformSyncState.SYNCING -> stringResource(
        R.string.syncmonitor_state_syncing,
        row.gamesDone,
        row.gamesTotal
    )
    PlatformSyncState.DONE -> stringResource(
        R.string.syncmonitor_state_done,
        row.added,
        row.updated,
        row.removed
    )
    PlatformSyncState.ALREADY_SYNCED -> stringResource(R.string.syncmonitor_state_already_synced)
    PlatformSyncState.FAILED -> row.error ?: stringResource(R.string.syncmonitor_state_failed)
}

/**
 * Eases toward each new value instead of jumping to it, so a bar fed by network responses reads
 * as motion rather than as a series of steps. It never runs ahead of the value it was given.
 */
@Composable
private fun InterpolatedProgressBar(
    target: Float,
    style: ProgressBarStyle,
    modifier: Modifier = Modifier
) {
    val animatable = remember { Animatable(target) }
    LaunchedEffect(target) {
        animatable.animateTo(targetValue = target, animationSpec = MotionTokens.Tween.medium)
    }
    ArgosyProgressBar(
        progress = animatable.value,
        style = style,
        modifier = modifier
    )
}

@Composable
private fun buildHints(state: SyncMonitorUiState): List<Pair<InputButton, String>> {
    val syncLabel = stringResource(R.string.syncmonitor_footer_sync)
    val backLabel = stringResource(R.string.syncmonitor_footer_back)
    return buildList {
        if (!state.isSyncing && state.isConnected) add(InputButton.Y to syncLabel)
        add(InputButton.B to backLabel)
    }
}
