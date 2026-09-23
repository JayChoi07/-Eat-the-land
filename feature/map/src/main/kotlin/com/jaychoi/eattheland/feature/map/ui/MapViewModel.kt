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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

/**
 * R-12-02: 플랜 B 에서 추적 중/아님 상태가 생기면 "상태별 허용 이벤트 다름" 1개 해당 → 여전히 MVVM-UDF.
 * 뷰포트 → region 집합은 값이 바뀔 때만 재구독한다(flatMapLatest + StateFlow 중복 제거).
 */
@HiltViewModel
class MapViewModel @Inject constructor(
    private val territory: TerritoryRepository,
    private val players: PlayerRepository,
    private val grid: HexGrid,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val regions = MutableStateFlow<Set<CellId>>(emptySet())
    private var initialized = false

    @OptIn(ExperimentalCoroutinesApi::class)
    fun initialize() {
        if (initialized) return
        initialized = true
        val cells = regions.flatMapLatest {
            if (it.isEmpty()) flowOf(emptyList()) else territory.observeCells(it)
        }
        combine(cells, players.currentPlayer) { list, player -> list to player }
            .onEach { (list, player) ->
                _uiState.update {
                    it.copy(player = player, cells = list.map { c -> c.toPolygon(player) })
                }
            }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: MapEvent) {
        when (event) {
            is MapEvent.CameraIdle -> onCameraIdle(event)
        }
    }

    private fun onCameraIdle(event: MapEvent.CameraIdle) {
        val zoomedOut = event.zoom < MIN_OVERLAY_ZOOM
        _uiState.update { it.copy(isZoomedOut = zoomedOut) }
        regions.value = if (zoomedOut) emptySet() else grid.regionsAround(event.center)
    }

    private fun Cell.toPolygon(me: Player?) = CellPolygon(
        id = id,
        points = grid.boundary(id),
        colorIndex = if (me != null && ownerUid == me.uid) null else ownerColor,
    )
}
