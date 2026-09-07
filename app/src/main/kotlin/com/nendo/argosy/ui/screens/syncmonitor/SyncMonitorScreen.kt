package com.nendo.argosy.ui.screens.syncmonitor

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.data.remote.romm.PlatformSyncRow
import com.nendo.argosy.data.remote.romm.PlatformSyncState
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.PlatformIconAssets
import com.nendo.argosy.ui.components.animateScrollToItemCentered
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.primitives.ArgosyProgressBar
import com.nendo.argosy.ui.primitives.ProgressBarStyle
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.ColorTokens
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
                        text = when {
                            uiState.isSyncing -> stringResource(R.string.syncmonitor_empty_starting)
                            uiState.isConnected -> stringResource(R.string.syncmonitor_empty_idle)
                            else -> stringResource(R.string.syncmonitor_empty_disconnected)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.textDim
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = Dimens.footerHeight + Dimens.spacingLg),
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
            text = when {
                state.isSyncing && state.hasRows -> stringResource(
                    R.string.syncmonitor_progress_platforms,
                    state.platformsDone,
                    state.rows.size
                )
                state.isSyncing -> stringResource(R.string.syncmonitor_progress_starting)
                else -> stringResource(R.string.syncmonitor_idle_subtitle)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textDim
        )
        if (state.isSyncing) {
            Spacer(Modifier.height(Dimens.spacingSm))
            if (state.hasRows) {
                InterpolatedProgressBar(
                    target = state.passFraction,
                    style = ProgressBarStyle.Active,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                ArgosyProgressBar(
                    progress = null,
                    style = ProgressBarStyle.Working,
                    modifier = Modifier.fillMaxWidth()
                )
            }
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

    val context = LocalContext.current
    val iconUri = remember(row.slug) { PlatformIconAssets.resolveAssetUri(context, row.slug) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(background)
            .clickableNoFocus(onClick = onClick)
            .padding(Dimens.spacingMd),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconUri != null) {
            AsyncImage(
                model = iconUri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(Dimens.iconLg)
            )
        } else {
            StateIcon(row.state)
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
        ) {
            Text(
                text = row.name,
                style = MaterialTheme.typography.titleSmall,
                color = theme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StateIcon(row.state)
                Text(
                    text = rowSubtitle(row),
                    style = MaterialTheme.typography.bodySmall,
                    color = stateColor(row.state),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (row.state == PlatformSyncState.SYNCING) {
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

        RowTrailing(row)
    }
}

/**
 * The line under the platform name: what this row is doing, in words, so the icon and colour are
 * confirmation rather than the only signal.
 */
@Composable
private fun rowSubtitle(row: PlatformSyncRow): String = when (row.state) {
    PlatformSyncState.IDLE -> stringResource(R.string.syncmonitor_state_idle)
    PlatformSyncState.QUEUED -> stringResource(R.string.syncmonitor_state_queued)
    PlatformSyncState.SYNCING -> stringResource(
        R.string.syncmonitor_state_syncing,
        row.gamesDone,
        row.gamesTotal
    )
    PlatformSyncState.DONE -> stringResource(R.string.syncmonitor_state_done_desc)
    PlatformSyncState.ALREADY_SYNCED -> stringResource(R.string.syncmonitor_state_already_synced)
    PlatformSyncState.FAILED -> row.error ?: stringResource(R.string.syncmonitor_state_failed)
}

@Composable
private fun successColor(): Color =
    if (LocalArgosyTheme.current.isDark) {
        ColorTokens.Semantic.Dark.success
    } else {
        ColorTokens.Semantic.Light.success
    }

@Composable
private fun warningColor(): Color =
    if (LocalArgosyTheme.current.isDark) {
        ColorTokens.Semantic.Dark.warning
    } else {
        ColorTokens.Semantic.Light.warning
    }

@Composable
private fun infoColor(): Color =
    if (LocalArgosyTheme.current.isDark) {
        ColorTokens.Semantic.Dark.info
    } else {
        ColorTokens.Semantic.Light.info
    }

@Composable
private fun progressColor(): Color =
    if (LocalArgosyTheme.current.isDark) {
        ColorTokens.Semantic.Dark.progress
    } else {
        ColorTokens.Semantic.Light.progress
    }

@Composable
private fun stateColor(state: PlatformSyncState): Color {
    val theme = LocalArgosyTheme.current
    return when (state) {
        PlatformSyncState.IDLE -> theme.textDim
        PlatformSyncState.QUEUED -> theme.textMute
        PlatformSyncState.SYNCING -> progressColor()
        PlatformSyncState.DONE -> successColor()
        PlatformSyncState.ALREADY_SYNCED -> theme.textMute
        PlatformSyncState.FAILED -> warningColor()
    }
}

@Composable
private fun StateIcon(state: PlatformSyncState) {
    Icon(
        imageVector = when (state) {
            PlatformSyncState.IDLE -> Icons.Default.Schedule
            PlatformSyncState.QUEUED -> Icons.Default.Schedule
            PlatformSyncState.SYNCING -> Icons.Default.Sync
            PlatformSyncState.DONE -> Icons.Default.CheckCircle
            PlatformSyncState.ALREADY_SYNCED -> Icons.Default.Check
            PlatformSyncState.FAILED -> Icons.Default.ErrorOutline
        },
        contentDescription = stateDescription(state),
        tint = stateColor(state),
        modifier = Modifier.size(Dimens.iconSm)
    )
}

@Composable
private fun stateDescription(state: PlatformSyncState): String = when (state) {
    PlatformSyncState.IDLE -> stringResource(R.string.syncmonitor_state_idle)
    PlatformSyncState.QUEUED -> stringResource(R.string.syncmonitor_state_queued)
    PlatformSyncState.SYNCING -> stringResource(R.string.syncmonitor_state_syncing_desc)
    PlatformSyncState.DONE -> stringResource(R.string.syncmonitor_state_done_desc)
    PlatformSyncState.ALREADY_SYNCED -> stringResource(R.string.syncmonitor_state_already_synced)
    PlatformSyncState.FAILED -> stringResource(R.string.syncmonitor_state_failed)
}

/**
 * What the row says on its right: counts once a platform is finished, its position in the pass
 * while it runs, and the server's own words when it failed.
 */
@Composable
private fun RowTrailing(row: PlatformSyncRow) {
    val theme = LocalArgosyTheme.current

    when (row.state) {
        PlatformSyncState.DONE -> Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (row.added > 0) {
                CountBadge(R.string.syncmonitor_badge_added, row.added, successColor())
            }
            if (row.updated > 0) {
                CountBadge(R.string.syncmonitor_badge_updated, row.updated, infoColor())
            }
            if (row.removed > 0) {
                CountBadge(R.string.syncmonitor_badge_removed, row.removed, warningColor())
            }
            if (row.added == 0 && row.updated == 0 && row.removed == 0) {
                Text(
                    text = stringResource(R.string.syncmonitor_badge_unchanged),
                    style = MaterialTheme.typography.labelMedium,
                    color = theme.textMute
                )
            }
        }
        PlatformSyncState.IDLE,
        PlatformSyncState.QUEUED,
        PlatformSyncState.SYNCING,
        PlatformSyncState.FAILED,
        PlatformSyncState.ALREADY_SYNCED -> Unit
    }
}

@Composable
private fun CountBadge(labelRes: Int, count: Int, color: Color) {
    Text(
        text = stringResource(labelRes, count),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.radiusPill))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs)
    )
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
