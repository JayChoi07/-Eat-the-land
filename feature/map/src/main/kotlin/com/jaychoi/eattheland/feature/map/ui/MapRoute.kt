package com.jaychoi.eattheland.feature.map.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.jaychoi.eattheland.core.common.intent.openAppSettings
import com.jaychoi.eattheland.core.designsystem.theme.TerritoryPalette
import com.jaychoi.eattheland.core.model.LatLngPoint

private val DEFAULT_CENTER = LatLngPoint(37.5665, 126.9780) // 서울시청. 권한이 있으면 마지막 위치로 바로 옮긴다
private const val DEFAULT_ZOOM = 16
private const val FILL_ALPHA = 0.4f

/** 산책 시작/종료는 :app 이 FGS 로 잇는다 — feature 는 콜백만 노출한다(스펙 §5). */
fun EntryProviderScope<NavKey>.mapEntry(
    onStartWalk: () -> Unit,
    onStopWalk: () -> Unit,
    onOpenRanking: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    entry<MapKey> {
        MapRoute(
            onStartWalk = onStartWalk,
            onStopWalk = onStopWalk,
            onOpenRanking = onOpenRanking,
            onOpenSettings = onOpenSettings,
        )
    }
}

@Composable
internal fun MapRoute(
    onStartWalk: () -> Unit,
    onStopWalk: () -> Unit,
    onOpenRanking: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 화면을 열 때 권한이 있으면 마지막 위치로 이동(사용자 결정 1). 없으면 조용히 기본 위치.
    LaunchedEffect(viewModel) {
        val granted = context.hasLocationPermission()
        viewModel.onEvent(MapEvent.LocationPermission(granted = granted, requested = false))
    }
    // 산책이 끝나면 추적 점이 멈춘 자리 대신 지금 위치를 다시 읽는다(첫 컴포지션에서도 한 번 — 무해).
    LaunchedEffect(uiState.isTracking) {
        if (!uiState.isTracking) viewModel.onEvent(MapEvent.WalkStopped)
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result.values.any { it }
        viewModel.onEvent(MapEvent.LocationPermission(granted = granted, requested = true))
        if (granted) onStartWalk()
    }

    val drawable = uiState.cells.map { drawableCell(it) }
    val myLocationStyle = MyLocationStyle(
        fillArgb = MaterialTheme.colorScheme.primary.toArgb(),
        ringArgb = MaterialTheme.colorScheme.surface.toArgb(),
    )
    val camera = uiState.camera

    MapScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onWalkToggle = {
            when {
                uiState.isTracking -> onStopWalk()
                context.hasLocationPermission() -> onStartWalk()
                else -> launcher.launch(locationPermissions())
            }
        },
        onOpenAppSettings = { context.openAppSettings() },
        onOpenRanking = onOpenRanking,
        onOpenSettings = onOpenSettings,
    ) {
        // attempt 가 바뀌면 컴포저블이 새로 만들어져 MapView.start 가 다시 돈다.
        key(uiState.mapAttempt) {
            KakaoMapView(
                cells = drawable,
                initialCenter = camera?.center ?: DEFAULT_CENTER,
                initialZoom = camera?.zoom ?: DEFAULT_ZOOM,
                followPoint = if (uiState.isFollowing) uiState.myLocation else null,
                myLocation = uiState.myLocation,
                myLocationStyle = myLocationStyle,
                onCameraIdle = { center, zoom, byUser ->
                    viewModel.onEvent(MapEvent.CameraIdle(center, zoom, byUser))
                },
                onMapError = { viewModel.onEvent(MapEvent.MapLoadFailed) },
                onMapClick = { viewModel.onEvent(MapEvent.MapTapped(it)) },
            )
        }
    }
}

@Composable
private fun drawableCell(cell: CellPolygon): DrawableCell {
    val color = TerritoryPalette.color(cell.colorIndex)
    return DrawableCell(
        id = cell.id.value,
        points = cell.points,
        fillArgb = color.copy(alpha = FILL_ALPHA).toArgb(),
        strokeArgb = color.toArgb(),
    )
}

private fun locationPermissions(): Array<String> = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

/** FGS location 타입은 coarse 만 있어도 시작할 수 있다. */
private fun Context.hasLocationPermission(): Boolean =
    locationPermissions().any { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
