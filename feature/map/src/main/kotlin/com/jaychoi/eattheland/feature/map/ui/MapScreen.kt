package com.jaychoi.eattheland.feature.map.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jaychoi.eattheland.core.designsystem.theme.AppTheme
import com.jaychoi.eattheland.core.model.Player
import com.jaychoi.eattheland.feature.map.R

/** 지도는 슬롯으로 받아 스크린샷 테스트가 SDK 없이 찍을 수 있게 한다. */
@Composable
fun MapScreen(
    uiState: MapUiState,
    modifier: Modifier = Modifier,
    map: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        map()
        uiState.player?.let { player ->
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp,
            ) {
                Text(
                    text = stringResource(
                        R.string.map_stat_cells,
                        player.nickname,
                        player.cellCount,
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        if (uiState.isZoomedOut) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Text(
                    text = stringResource(R.string.map_zoomed_out_hint),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Preview
@Composable
private fun MapScreenPreview() {
    AppTheme { MapScreen(MapUiState(player = Player("u", "땅주인", 0, 42), isZoomedOut = true)) { } }
}
