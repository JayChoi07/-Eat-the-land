package com.jaychoi.eattheland.feature.map.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jaychoi.eattheland.core.designsystem.theme.AppTheme
import com.jaychoi.eattheland.core.model.Player
import com.jaychoi.eattheland.feature.map.R

/** 지도는 슬롯으로 받아 스크린샷 테스트가 SDK 없이 찍을 수 있게 한다. 상태와 콜백만 받는다 (R-17-01). */
@Composable
fun MapScreen(
    uiState: MapUiState,
    onEvent: (MapEvent) -> Unit,
    onWalkToggle: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    map: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        map()
        uiState.player?.let { player ->
            StatusChip(uiState, player, Modifier.align(Alignment.TopCenter).padding(16.dp))
        }
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (uiState.isZoomedOut) Hint(stringResource(R.string.map_zoomed_out_hint))
            if (uiState.showPermissionNotice) {
                PermissionNotice(
                    onOpenSettings = onOpenSettings,
                    onDismiss = { onEvent(MapEvent.PermissionNoticeDismissed) },
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onWalkToggle) {
                Text(
                    stringResource(
                        if (uiState.isTracking) R.string.map_walk_stop else R.string.map_walk_start,
                    ),
                )
            }
        }
        FilledTonalIconButton(
            onClick = { onEvent(MapEvent.MyLocationClicked) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
        ) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = stringResource(R.string.map_my_location),
            )
        }
        if (uiState.mapLoadFailed) {
            MapLoadFailed(onRetry = { onEvent(MapEvent.RetryMap) })
        }
    }
}

@Composable
private fun StatusChip(uiState: MapUiState, player: Player, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.map_stat_cells, player.nickname, player.cellCount),
                style = MaterialTheme.typography.titleMedium,
            )
            if (uiState.isTracking) {
                Text(
                    stringResource(R.string.map_walk_count, uiState.walkCellCount),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (uiState.pendingCount > 0) {
                Text(
                    stringResource(R.string.map_pending_count, uiState.pendingCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (uiState.isGpsWeak) {
                Text(
                    stringResource(R.string.map_gps_weak),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun PermissionNotice(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.map_permission_notice))
            Row(horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.map_dismiss)) }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.map_open_settings))
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

/** 카카오 인증·통신 오류. 다시 시도는 MapView 를 새로 만든다(스펙 §5). */
@Composable
private fun MapLoadFailed(onRetry: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.map_load_failed),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) { Text(stringResource(R.string.map_retry)) }
        }
    }
}

@Preview
@Composable
private fun MapScreenPreview() {
    AppTheme {
        MapScreen(
            MapUiState(
                player = Player("u", "땅주인", 0, 42),
                isTracking = true,
                walkCellCount = 3,
                pendingCount = 2,
                isGpsWeak = true,
            ),
            onEvent = {},
            onWalkToggle = {},
            onOpenSettings = {},
        ) { }
    }
}
