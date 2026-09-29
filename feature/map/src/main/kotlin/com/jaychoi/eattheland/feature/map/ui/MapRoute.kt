package com.jaychoi.eattheland.feature.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.toArgb
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.jaychoi.eattheland.core.designsystem.theme.TerritoryPalette
import com.jaychoi.eattheland.core.model.LatLngPoint

private val DEFAULT_CENTER = LatLngPoint(37.5665, 126.9780) // 서울시청. 현재 위치 연동은 Task 8
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

    val camera = uiState.camera
    MapScreen(uiState = uiState, onEvent = viewModel::onEvent) {
        // attempt 가 바뀌면 컴포저블이 새로 만들어져 MapView.start 가 다시 돈다.
        key(uiState.mapAttempt) {
            KakaoMapView(
                cells = drawable,
                initialCenter = camera?.center ?: DEFAULT_CENTER,
                initialZoom = camera?.zoom ?: DEFAULT_ZOOM,
                onCameraIdle = { center, zoom ->
                    viewModel.onEvent(MapEvent.CameraIdle(center, zoom))
                },
                onMapError = { viewModel.onEvent(MapEvent.MapLoadFailed) },
            )
        }
    }
}
