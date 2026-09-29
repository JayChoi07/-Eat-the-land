package com.jaychoi.eattheland.feature.map.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaychoi.eattheland.core.common.Clock
import com.jaychoi.eattheland.core.common.grid.HexGrid
import com.jaychoi.eattheland.core.data.PlayerRepository
import com.jaychoi.eattheland.core.data.TerritoryRepository
import com.jaychoi.eattheland.core.data.location.LocationRepository
import com.jaychoi.eattheland.core.data.tracking.TrackingRepository
import com.jaychoi.eattheland.core.model.Cell
import com.jaychoi.eattheland.core.model.CellId
import com.jaychoi.eattheland.core.model.LatLngPoint
import com.jaychoi.eattheland.core.model.Player
import com.jaychoi.eattheland.core.model.TrackingState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * R-12-02: "상태별 허용 이벤트 다름"(Idle↔Tracking — CTA 가 시작/종료로 바뀜) 1개 → MVVM-UDF.
 * 뷰포트 → region 집합은 값이 바뀔 때만 재구독한다(flatMapLatest + 중복 제거).
 * 셀 리스너는 화면이 수집하는 동안만 산다 — 앱이 백그라운드로 가면 5초 뒤 끊겨 Firestore read 를 쓰지 않는다.
 * 산책 시작/종료는 서비스(:app)가 하므로 여기엔 없다 — 화면은 TrackingRepository.state 를 읽기만 한다.
 */
@HiltViewModel
class MapViewModel @Inject constructor(
    private val territory: TerritoryRepository,
    private val players: PlayerRepository,
    private val grid: HexGrid,
    private val tracking: TrackingRepository,
    private val locations: LocationRepository,
    private val clock: Clock,
) : ViewModel() {

    /** 이 화면 안에서만 사는 상태. 스트림(셀·플레이어·추적·큐)과 combine 해 UiState 가 된다. */
    private data class Local(
        val regions: Set<CellId> = emptySet(),
        val isZoomedOut: Boolean = false,
        val mapLoadFailed: Boolean = false,
        val mapAttempt: Int = 0,
        val camera: CameraSnapshot? = null,
        val lastKnown: LatLngPoint? = null,
        val isFollowing: Boolean = true,
        val showPermissionNotice: Boolean = false,
        val selected: Selection? = null,
    )

    /** 탭한 셀 + 그때 산책 중이었는지 — 산책 상태가 바뀌면 카드를 닫는다(스펙 C §9). */
    private data class Selection(val cell: SelectedCell, val whileTracking: Boolean)

    private val local = MutableStateFlow(Local())
    private var latestCells: List<Cell> = emptyList()
    private var cardTimer: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    private val cells = local.map { it.regions }
        .distinctUntilChanged()
        .flatMapLatest(::cellsIn)
        .onEach { latestCells = it }

    /** 추적 상태 + 지금 시각. 산책 중일 때만 1초마다 시각이 흘러 경과 시간이 다시 그려진다(스펙 C §8). */
    private data class WalkView(val state: TrackingState, val nowMillis: Long?)

    private val ticker = flow {
        while (true) {
            emit(clock.nowMillis())
            delay(TICK_MS)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val walk: Flow<WalkView> = tracking.state.flatMapLatest { s ->
        if (s.isTracking) ticker.map { WalkView(s, it) } else flowOf(WalkView(s, null))
    }

    val uiState: StateFlow<MapUiState> = combine(
        cells,
        players.currentPlayer,
        walk,
        territory.pendingCount,
        local,
    ) { list, player, view, pending, l ->
        toUiState(list, player, view, pending, l)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MapUiState())

    fun onEvent(event: MapEvent) {
        when (event) {
            is MapEvent.CameraIdle -> onCameraIdle(event)

            MapEvent.MapLoadFailed -> local.update { it.copy(mapLoadFailed = true) }

            MapEvent.RetryMap -> local.update {
                it.copy(mapLoadFailed = false, mapAttempt = it.mapAttempt + 1)
            }

            MapEvent.MyLocationClicked -> {
                local.update { it.copy(isFollowing = true) }
                refreshLastKnown()
            }

            is MapEvent.LocationPermission -> onPermission(event)

            MapEvent.PermissionNoticeDismissed -> local.update {
                it.copy(showPermissionNotice = false)
            }

            MapEvent.WalkStopped -> refreshLastKnown()

            MapEvent.SummaryDismissed -> tracking.onSummaryDismissed()

            is MapEvent.MapTapped -> onMapTapped(event.point)

            MapEvent.CellCardDismissed -> closeCard()
        }
    }

    private fun toUiState(
        list: List<Cell>,
        player: Player?,
        view: WalkView,
        pending: Int,
        l: Local,
    ): MapUiState {
        val walk = view.state
        return MapUiState(
            player = player,
            cells = list.map { it.toPolygon(player) },
            isZoomedOut = l.isZoomedOut,
            mapLoadFailed = l.mapLoadFailed,
            mapAttempt = l.mapAttempt,
            camera = l.camera,
            // 산책 중엔 추적 점, 끝나면 새로 읽은 마지막 위치(WalkStopped 가 갱신)를 우선한다.
            myLocation = if (walk.isTracking) {
                walk.lastPoint ?: l.lastKnown
            } else {
                l.lastKnown ?: walk.lastPoint
            },
            isFollowing = l.isFollowing,
            isTracking = walk.isTracking,
            walkCellCount = walk.capturedCount,
            distanceMeters = walk.distanceMeters,
            elapsedMillis = elapsedOf(walk, view.nowMillis),
            pendingCount = pending,
            // 줌 아웃 안내와 같은 자리를 쓰므로 둘이 동시에 뜨지 않는다.
            isGpsWeak = walk.isTracking && walk.isGpsWeak && !l.isZoomedOut,
            showPermissionNotice = l.showPermissionNotice,
            summary = walk.lastSummary.takeIf { !walk.isTracking },
            selectedCell = l.selected?.takeIf { it.whileTracking == walk.isTracking }?.cell,
        )
    }

    private fun elapsedOf(walk: TrackingState, nowMillis: Long?): Long? {
        val startedAt = walk.startedAtMillis ?: return null
        val now = nowMillis ?: return null
        return (now - startedAt).coerceAtLeast(0L)
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
                isFollowing = it.isFollowing && !event.byUser,
            )
        }
    }

    // 화면을 열며 확인만 한 경우(requested=false)는 따라가기 설정을 건드리지 않는다 — 회전 뒤 재확인이 사용자의 해제를 풀지 않게.
    private fun onPermission(event: MapEvent.LocationPermission) {
        if (event.granted) {
            if (event.requested) local.update { it.copy(isFollowing = true) }
            refreshLastKnown()
        } else if (event.requested) {
            local.update { it.copy(showPermissionNotice = true) }
        }
    }

    // 권한 확인은 Route 가 한다. 권한이 없으면 lastKnown 이 null 을 준다.
    private fun refreshLastKnown() {
        viewModelScope.launch {
            val point = locations.lastKnown() ?: return@launch
            local.update { it.copy(lastKnown = point) }
        }
    }

    private fun onMapTapped(point: LatLngPoint) {
        val id = grid.cellOf(point)
        val cell = latestCells.firstOrNull { it.id == id }
        val current = local.value.selected?.cell
        // 중립 셀이거나 같은 셀을 다시 탭하면 닫는다.
        if (cell == null || current?.id == id) {
            closeCard()
            return
        }
        val me = uiState.value.player
        val owner = if (me != null && cell.ownerUid == me.uid) CellOwner.Me else CellOwner.Loading
        val time = relativeTime(clock.nowMillis(), cell.walkedAtMillis)
        select(SelectedCell(id, owner, time))
        if (owner == CellOwner.Loading) loadOwner(id, cell.ownerUid)
    }

    private fun select(cell: SelectedCell) {
        val whileTracking = uiState.value.isTracking
        local.update { it.copy(selected = Selection(cell, whileTracking)) }
        cardTimer?.cancel()
        cardTimer = viewModelScope.launch {
            delay(CARD_TIMEOUT_MS)
            closeCard()
        }
    }

    private fun loadOwner(id: CellId, ownerUid: String) {
        viewModelScope.launch {
            val owner = players.nicknameOf(ownerUid)?.let { CellOwner.Named(it) } ?: CellOwner.Gone
            local.update { l ->
                val s = l.selected
                if (s?.cell?.id == id) {
                    l.copy(selected = s.copy(cell = s.cell.copy(owner = owner)))
                } else {
                    l
                }
            }
        }
    }

    private fun closeCard() {
        cardTimer?.cancel()
        cardTimer = null
        local.update { it.copy(selected = null) }
    }

    private fun Cell.toPolygon(me: Player?) = CellPolygon(
        id = id,
        points = grid.boundary(id),
        colorIndex = if (me != null && ownerUid == me.uid) null else ownerColor,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val TICK_MS = 1_000L
        const val CARD_TIMEOUT_MS = 5_000L
    }
}
