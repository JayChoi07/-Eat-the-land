package com.jaychoi.eattheland.feature.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.jaychoi.eattheland.core.designsystem.theme.TerritoryPalette
import com.jaychoi.eattheland.core.model.LatLngPoint

private val DEFAULT_CENTER = LatLngPoint(37.5665, 126.9780) // 서울시청. 현재 위치 연동은 플랜 B
private const val DEFAULT_ZOOM = 16
private const val FILL_ALPHA = 0.4f

fun EntryProviderScope<NavKey>.mapEntry() {
    entry<MapKey> { MapRoute() }
}

@Composable
internal fun MapRoute(viewModel: MapViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val drawable = uiState.cells.map { cell ->
        val color = TerritoryPalette.color(cell.colorIndex)
        DrawableCell(
            id = cell.id.value,
            points = cell.points,
            fillArgb = color.copy(alpha = FILL_ALPHA).toArgb(),
            strokeArgb = color.toArgb(),
        )
    }

    MapScreen(uiState = uiState) {
        KakaoMapView(
            cells = drawable,
            initialCenter = DEFAULT_CENTER,
            initialZoom = DEFAULT_ZOOM,
            onCameraIdle = { center, zoom -> viewModel.onEvent(MapEvent.CameraIdle(center, zoom)) },
        )
    }
}
