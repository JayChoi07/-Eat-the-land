package com.jaychoi.eattheland.feature.map.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaychoi.eattheland.core.common.grid.HexGrid
import com.jaychoi.eattheland.core.data.PlayerRepository
import com.jaychoi.eattheland.core.data.TerritoryRepository
import com.jaychoi.eattheland.core.model.Cell
import com.jaychoi.eattheland.core.model.CellId
import com.jaychoi.eattheland.core.model.Player
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * R-12-02: 플랜 B 에서 추적 중/아님 상태가 생기면 "상태별 허용 이벤트 다름" 1개 해당 → 여전히 MVVM-UDF.
 * 뷰포트 → region 집합은 값이 바뀔 때만 재구독한다(flatMapLatest + 중복 제거).
 * 셀 리스너는 화면이 수집하는 동안만 산다 — 앱이 백그라운드로 가면 5초 뒤 끊겨 Firestore read 를 쓰지 않는다.
 */
@HiltViewModel
class MapViewModel @Inject constructor(
    private val territory: TerritoryRepository,
    players: PlayerRepository,
    private val grid: HexGrid,
) : ViewModel() {

    /** 이 화면 안에서만 사는 상태. 스트림(셀·플레이어)과 combine 해 UiState 가 된다. */
    private data class Local(
        val regions: Set<CellId> = emptySet(),
        val isZoomedOut: Boolean = false,
        val mapLoadFailed: Boolean = false,
        val mapAttempt: Int = 0,
        val camera: CameraSnapshot? = null,
    )

    private val local = MutableStateFlow(Local())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val cells = local.map { it.regions }
        .distinctUntilChanged()
        .flatMapLatest(::cellsIn)

    val uiState: StateFlow<MapUiState> = combine(
        cells,
        players.currentPlayer,
        local,
    ) { list, player, l ->
        MapUiState(
            player = player,
            cells = list.map { it.toPolygon(player) },
            isZoomedOut = l.isZoomedOut,
            mapLoadFailed = l.mapLoadFailed,
            mapAttempt = l.mapAttempt,
            camera = l.camera,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MapUiState())

    fun onEvent(event: MapEvent) {
        when (event) {
            is MapEvent.CameraIdle -> onCameraIdle(event)

            MapEvent.MapLoadFailed -> local.update { it.copy(mapLoadFailed = true) }

            MapEvent.RetryMap -> local.update {
                it.copy(mapLoadFailed = false, mapAttempt = it.mapAttempt + 1)
            }
        }
    }

    private fun cellsIn(regions: Set<CellId>): Flow<List<Cell>> =
        if (regions.isEmpty()) flowOf(emptyList()) else territory.observeCells(regions)

    private fun onCameraIdle(event: MapEvent.CameraIdle) {
        val zoomedOut = event.zoom < MIN_OVERLAY_ZOOM
        local.update {
            it.copy(
                regions = if (zoomedOut) emptySet() else grid.regionsAround(event.center),
                isZoomedOut = zoomedOut,
                camera = CameraSnapshot(event.center, event.zoom.toInt()),
            )
        }
    }

    private fun Cell.toPolygon(me: Player?) = CellPolygon(
        id = id,
        points = grid.boundary(id),
        colorIndex = if (me != null && ownerUid == me.uid) null else ownerColor,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
