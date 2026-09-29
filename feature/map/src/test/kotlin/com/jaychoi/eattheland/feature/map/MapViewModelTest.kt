package com.jaychoi.eattheland.feature.map

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.jaychoi.eattheland.core.model.Cell
import com.jaychoi.eattheland.core.model.LatLngPoint
import com.jaychoi.eattheland.core.model.Player
import com.jaychoi.eattheland.core.model.WalkSummary
import com.jaychoi.eattheland.core.testing.FakeHexGrid
import com.jaychoi.eattheland.core.testing.FakeLocationRepository
import com.jaychoi.eattheland.core.testing.FakePlayerRepository
import com.jaychoi.eattheland.core.testing.FakeTerritoryRepository
import com.jaychoi.eattheland.core.testing.FakeTrackingRepository
import com.jaychoi.eattheland.core.testing.MainDispatcherRule
import com.jaychoi.eattheland.feature.map.ui.CameraSnapshot
import com.jaychoi.eattheland.feature.map.ui.CellOwner
import com.jaychoi.eattheland.feature.map.ui.MapEvent
import com.jaychoi.eattheland.feature.map.ui.MapUiState
import com.jaychoi.eattheland.feature.map.ui.MapViewModel
import com.jaychoi.eattheland.feature.map.ui.RelativeTime
import com.jaychoi.eattheland.feature.map.ui.SelectedCell
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MapViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val territory = FakeTerritoryRepository()
    private val players = FakePlayerRepository()
    private val grid = FakeHexGrid()
    private val tracking = FakeTrackingRepository()
    private val locations = FakeLocationRepository()
    private val seoul = LatLngPoint(37.5661, 126.9780)

    private var now = 0L
    private fun viewModel() = MapViewModel(territory, players, grid, tracking, locations) { now }

    private fun seedTwoCells() {
        val mine = grid.cellOf(seoul)
        val other = grid.cellOf(LatLngPoint(37.5679, 126.9780))
        territory.cells.value = listOf(
            Cell(mine, "me", 0, 0, grid.regionOf(mine)),
            Cell(other, "u2", 4, 1_000_000_000L, grid.regionOf(other)),
        )
    }

    @Test
    fun `카메라가 멈추면 그 region 의 셀을 구독하고 폴리곤으로 바꾼다`() = runTest {
        val me = Player("me", "나", 0, 3)
        players.playerFlow.value = me
        seedTwoCells()
        val mine = grid.cellOf(seoul)
        val other = grid.cellOf(LatLngPoint(37.5679, 126.9780))
        val vm = viewModel()
        vm.uiState.test {
            vm.onEvent(MapEvent.CameraIdle(seoul, zoom = 16f, byUser = false))
            val state = awaitItemUntil { it.cells.size == 2 }
            assertEquals(setOf(grid.regionOf(mine)), territory.requestedRegions.last())
            assertNull(state.cells.first { it.id == mine }.colorIndex)
            assertEquals(4, state.cells.first { it.id == other }.colorIndex)
            assertEquals(me, state.player)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `줌 15 미만이면 구독하지 않고 isZoomedOut`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            vm.onEvent(MapEvent.CameraIdle(seoul, zoom = 14.9f, byUser = false))
            val state = awaitItemUntil { it.isZoomedOut }
            assertTrue(state.cells.isEmpty())
            assertTrue(territory.requestedRegions.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `줌이 정확히 15 면 구독한다`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            vm.onEvent(MapEvent.CameraIdle(seoul, zoom = 15f, byUser = false))
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, territory.requestedRegions.size)
    }

    @Test
    fun `확대했다가 축소하면 폴리곤을 지우고 셀 구독을 끊는다`() = runTest {
        seedTwoCells()
        val vm = viewModel()
        vm.uiState.test {
            vm.onEvent(MapEvent.CameraIdle(seoul, zoom = 16f, byUser = false))
            awaitItemUntil { it.cells.size == 2 }
            assertEquals(1, territory.cells.subscriptionCount.value)
            vm.onEvent(MapEvent.CameraIdle(seoul, zoom = 13f, byUser = false))
            awaitItemUntil { it.isZoomedOut && it.cells.isEmpty() }
            assertEquals(0, territory.cells.subscriptionCount.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `화면이 안 보이면(수집자 없음) 5초 뒤 셀 구독을 끊는다`() = runTest {
        seedTwoCells()
        val vm = viewModel()
        vm.uiState.test {
            vm.onEvent(MapEvent.CameraIdle(seoul, zoom = 16f, byUser = false))
            awaitItemUntil { it.cells.size == 2 }
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, territory.cells.subscriptionCount.value)
        advanceTimeBy(5_001)
        runCurrent()
        assertEquals(0, territory.cells.subscriptionCount.value)
    }

    @Test
    fun `같은 region 안에서 카메라가 움직이면 재구독하지 않는다`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            vm.onEvent(MapEvent.CameraIdle(seoul, zoom = 16f, byUser = false))
            vm.onEvent(
                MapEvent.CameraIdle(LatLngPoint(37.5662, 126.9781), zoom = 17f, byUser = false),
            )
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, territory.requestedRegions.size)
    }

    @Test
    fun `지도 시작 실패는 mapLoadFailed, 다시 시도는 attempt 를 올리고 실패를 지운다`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            vm.onEvent(MapEvent.MapLoadFailed)
            val failed = awaitItemUntil { it.mapLoadFailed }
            assertEquals(0, failed.mapAttempt)
            vm.onEvent(MapEvent.RetryMap)
            val retried = awaitItemUntil { it.mapAttempt == 1 }
            assertEquals(false, retried.mapLoadFailed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `카메라가 멈춘 위치·줌을 기억한다`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            vm.onEvent(MapEvent.CameraIdle(seoul, zoom = 15.7f, byUser = false))
            val state = awaitItemUntil { it.camera != null }
            assertEquals(CameraSnapshot(seoul, zoom = 15), state.camera)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `산책 상태·이번 산책 칸 수·GPS 약함·전송 대기가 UiState 에 비친다`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            tracking.onWalkStarted(0L)
            tracking.onCaptured()
            tracking.onLocation(seoul, isGpsWeak = true)
            territory.pending.value = 2
            val state = awaitItemUntil {
                it.isTracking && it.walkCellCount == 1 && it.pendingCount == 2
            }
            assertTrue(state.isGpsWeak)
            assertEquals(seoul, state.myLocation)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `권한이 있으면 마지막 위치를 내 위치로 두고 따라간다`() = runTest {
        locations.lastKnownPoint = seoul
        val vm = viewModel()
        vm.uiState.test {
            vm.onEvent(MapEvent.LocationPermission(granted = true, requested = false))
            val state = awaitItemUntil { it.myLocation == seoul }
            assertTrue(state.isFollowing)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `요청 뒤 거부면 안내를 띄우고 닫으면 사라진다 - 처음 확인만 한 거부는 안내 없음`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            vm.onEvent(MapEvent.LocationPermission(granted = false, requested = false))
            expectNoEvents()
            vm.onEvent(MapEvent.LocationPermission(granted = false, requested = true))
            awaitItemUntil { it.showPermissionNotice }
            vm.onEvent(MapEvent.PermissionNoticeDismissed)
            awaitItemUntil { !it.showPermissionNotice }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `따라가기를 끈 뒤 화면 재생성의 권한 확인은 따라가기를 다시 켜지 않는다`() = runTest {
        locations.lastKnownPoint = seoul
        val vm = viewModel()
        vm.uiState.test {
            vm.onEvent(MapEvent.CameraIdle(seoul, zoom = 16f, byUser = true))
            awaitItemUntil { !it.isFollowing }
            vm.onEvent(MapEvent.LocationPermission(granted = true, requested = false))
            val state = awaitItemUntil { it.myLocation == seoul }
            assertEquals(false, state.isFollowing)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `산책 중엔 추적 위치, 산책이 끝나면 새로 읽은 마지막 위치를 쓴다`() = runTest {
        val walkPoint = LatLngPoint(37.5700, 126.9800)
        val after = LatLngPoint(37.5800, 126.9900)
        locations.lastKnownPoint = seoul
        val vm = viewModel()
        vm.uiState.test {
            vm.onEvent(MapEvent.LocationPermission(granted = true, requested = false))
            awaitItemUntil { it.myLocation == seoul }
            tracking.onWalkStarted(0L)
            tracking.onLocation(walkPoint, isGpsWeak = false)
            awaitItemUntil { it.myLocation == walkPoint }
            tracking.onWalkStopped(0L)
            locations.lastKnownPoint = after
            vm.onEvent(MapEvent.WalkStopped)
            awaitItemUntil { it.myLocation == after }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `사용자가 지도를 움직이면 따라가기 해제, 내 위치 버튼으로 복귀`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            assertTrue(awaitItem().isFollowing)
            vm.onEvent(MapEvent.CameraIdle(seoul, zoom = 16f, byUser = true))
            awaitItemUntil { !it.isFollowing }
            vm.onEvent(MapEvent.CameraIdle(seoul, zoom = 16f, byUser = false)) // 프로그램 이동은 유지
            expectNoEvents()
            vm.onEvent(MapEvent.MyLocationClicked)
            awaitItemUntil { it.isFollowing }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `산책 중엔 거리와 1초마다 갱신되는 경과 시간, 끝나면 경과 시간은 null`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            assertNull(awaitItem().elapsedMillis)
            now = 10_000L
            tracking.onWalkStarted(nowMillis = 10_000L)
            tracking.onDistance(1_830.0)
            val started = awaitItemUntil { it.isTracking && it.distanceMeters == 1_830.0 }
            assertEquals(0L, started.elapsedMillis)
            now = 13_000L
            advanceTimeBy(3_001)
            assertEquals(3_000L, awaitItemUntil { it.elapsedMillis == 3_000L }.elapsedMillis)
            tracking.onWalkStopped(nowMillis = 13_000L)
            assertNull(awaitItemUntil { !it.isTracking }.elapsedMillis)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `줌 아웃 상태에서는 GPS 배너를 숨긴다`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            tracking.onWalkStarted(nowMillis = 0L)
            tracking.onLocation(seoul, isGpsWeak = true)
            assertTrue(awaitItemUntil { it.isGpsWeak }.isGpsWeak)
            vm.onEvent(MapEvent.CameraIdle(seoul, zoom = 13f, byUser = false))
            assertEquals(false, awaitItemUntil { it.isZoomedOut }.isGpsWeak)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `산책이 끝나면 요약이 뜨고, 닫으면 사라지며, 다시 시작해도 옛 요약은 뜨지 않는다`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            assertNull(awaitItem().summary)
            tracking.onWalkStarted(nowMillis = 0L)
            tracking.onCaptured()
            tracking.onDistance(320.0)
            awaitItemUntil { it.isTracking }
            tracking.onWalkStopped(nowMillis = 90_000L)
            val ended = awaitItemUntil { it.summary != null }
            assertEquals(WalkSummary(0L, 90_000L, cells = 1, meters = 320.0), ended.summary)
            vm.onEvent(MapEvent.SummaryDismissed)
            assertNull(awaitItemUntil { it.summary == null }.summary)
            tracking.onWalkStarted(nowMillis = 100_000L)
            assertNull(awaitItemUntil { it.isTracking }.summary)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `산책 중에는 요약을 보이지 않는다`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            tracking.onWalkStarted(nowMillis = 0L)
            tracking.onWalkStopped(nowMillis = 1_000L)
            awaitItemUntil { it.summary != null }
            tracking.onWalkStarted(nowMillis = 2_000L) // 요약을 안 닫고 바로 재시작
            assertNull(awaitItemUntil { it.isTracking }.summary)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private val otherPoint = LatLngPoint(37.5679, 126.9780)

    /** 셀 2개(내 것·u2 것)를 심고 카메라를 멈춰 셀이 로드된 상태로 만든다. */
    private suspend fun ReceiveTurbine<MapUiState>.loadCells(vm: MapViewModel) {
        vm.onEvent(MapEvent.CameraIdle(seoul, zoom = 16f, byUser = false))
        awaitItemUntil { it.cells.size == 2 }
    }

    @Test
    fun `남의 셀을 탭하면 닉네임을 조회해 카드로 보인다`() = runTest {
        players.playerFlow.value = Player("me", "나", 0, 3)
        players.nicknames["u2"] = "산책왕"
        now = 1_000_000_000L + 3 * 60 * 60_000L
        seedTwoCells()
        val vm = viewModel()
        vm.uiState.test {
            loadCells(vm)
            vm.onEvent(MapEvent.MapTapped(otherPoint))
            val shown = awaitItemUntil { it.selectedCell?.owner is CellOwner.Named }
            assertEquals(
                SelectedCell(
                    grid.cellOf(otherPoint),
                    CellOwner.Named("산책왕"),
                    RelativeTime.Hours(3),
                ),
                shown.selectedCell,
            )
            assertEquals(listOf("u2"), players.nicknameOfCalls)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `내 셀은 닉네임 조회 없이 내 땅`() = runTest {
        players.playerFlow.value = Player("me", "나", 0, 3)
        seedTwoCells()
        val vm = viewModel()
        vm.uiState.test {
            loadCells(vm)
            vm.onEvent(MapEvent.MapTapped(seoul))
            val shown = awaitItemUntil { it.selectedCell != null }
            assertEquals(CellOwner.Me, shown.selectedCell?.owner)
            assertTrue(players.nicknameOfCalls.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `소유자 문서가 없으면 떠난 사람`() = runTest {
        players.playerFlow.value = Player("me", "나", 0, 3)
        seedTwoCells() // u2 는 nicknames 에 없음
        val vm = viewModel()
        vm.uiState.test {
            loadCells(vm)
            vm.onEvent(MapEvent.MapTapped(otherPoint))
            val gone = awaitItemUntil { it.selectedCell?.owner == CellOwner.Gone }
            assertEquals(CellOwner.Gone, gone.selectedCell?.owner)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `중립 셀 탭·같은 셀 재탭·명시적 닫기는 카드를 닫는다`() = runTest {
        players.playerFlow.value = Player("me", "나", 0, 3)
        players.nicknames["u2"] = "산책왕"
        seedTwoCells()
        val vm = viewModel()
        vm.uiState.test {
            loadCells(vm)
            vm.onEvent(MapEvent.MapTapped(otherPoint))
            awaitItemUntil { it.selectedCell != null }
            vm.onEvent(MapEvent.MapTapped(LatLngPoint(37.6000, 126.9000))) // 중립
            awaitItemUntil { it.selectedCell == null }
            vm.onEvent(MapEvent.MapTapped(otherPoint))
            awaitItemUntil { it.selectedCell != null }
            vm.onEvent(MapEvent.MapTapped(otherPoint)) // 같은 셀 재탭
            awaitItemUntil { it.selectedCell == null }
            vm.onEvent(MapEvent.MapTapped(otherPoint))
            awaitItemUntil { it.selectedCell != null }
            vm.onEvent(MapEvent.CellCardDismissed)
            awaitItemUntil { it.selectedCell == null }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `5초 뒤 자동으로 닫힌다`() = runTest {
        players.playerFlow.value = Player("me", "나", 0, 3)
        players.nicknames["u2"] = "산책왕"
        seedTwoCells()
        val vm = viewModel()
        vm.uiState.test {
            loadCells(vm)
            vm.onEvent(MapEvent.MapTapped(otherPoint))
            awaitItemUntil { it.selectedCell?.owner is CellOwner.Named }
            advanceTimeBy(4_999)
            runCurrent()
            expectNoEvents()
            advanceTimeBy(2)
            awaitItemUntil { it.selectedCell == null }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `닫은 뒤 늦게 온 닉네임 응답이 새 카드를 덮어쓰지 않는다`() = runTest {
        players.playerFlow.value = Player("me", "나", 0, 3)
        players.nicknames["u2"] = "산책왕"
        players.nicknameDelayMs = 1_000L
        seedTwoCells()
        val other = grid.cellOf(otherPoint)
        val vm = viewModel()
        vm.uiState.test {
            loadCells(vm)
            vm.onEvent(MapEvent.MapTapped(otherPoint))
            awaitItemUntil { it.selectedCell?.owner == CellOwner.Loading }
            vm.onEvent(MapEvent.CellCardDismissed)
            awaitItemUntil { it.selectedCell == null }
            // 그 사이 내가 그 셀을 잡았다.
            territory.cells.value = territory.cells.value.map {
                if (it.id == other) it.copy(ownerUid = "me") else it
            }
            awaitItemUntil { s -> s.cells.first { it.id == other }.colorIndex == null }
            vm.onEvent(MapEvent.MapTapped(otherPoint))
            awaitItemUntil { it.selectedCell?.owner == CellOwner.Me }
            advanceTimeBy(1_001) // 옛 조회가 이제 응답한다
            runCurrent()
            expectNoEvents()
            assertEquals(CellOwner.Me, vm.uiState.value.selectedCell?.owner)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `산책이 시작되면 카드가 닫힌다`() = runTest {
        players.playerFlow.value = Player("me", "나", 0, 3)
        players.nicknames["u2"] = "산책왕"
        seedTwoCells()
        val vm = viewModel()
        vm.uiState.test {
            loadCells(vm)
            vm.onEvent(MapEvent.MapTapped(otherPoint))
            awaitItemUntil { it.selectedCell != null }
            tracking.onWalkStarted(nowMillis = 0L)
            assertNull(awaitItemUntil { it.isTracking }.selectedCell)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

/** turbine 보조: 조건을 만족하는 첫 아이템까지 소비한다. */
private suspend fun <T> ReceiveTurbine<T>.awaitItemUntil(predicate: (T) -> Boolean): T {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}
