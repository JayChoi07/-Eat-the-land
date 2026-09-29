package com.jaychoi.eattheland.feature.map

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.jaychoi.eattheland.core.model.Cell
import com.jaychoi.eattheland.core.model.LatLngPoint
import com.jaychoi.eattheland.core.model.Player
import com.jaychoi.eattheland.core.testing.FakeHexGrid
import com.jaychoi.eattheland.core.testing.FakeLocationRepository
import com.jaychoi.eattheland.core.testing.FakePlayerRepository
import com.jaychoi.eattheland.core.testing.FakeTerritoryRepository
import com.jaychoi.eattheland.core.testing.FakeTrackingRepository
import com.jaychoi.eattheland.core.testing.MainDispatcherRule
import com.jaychoi.eattheland.feature.map.ui.CameraSnapshot
import com.jaychoi.eattheland.feature.map.ui.MapEvent
import com.jaychoi.eattheland.feature.map.ui.MapViewModel
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

    private fun viewModel() = MapViewModel(territory, players, grid, tracking, locations)

    private fun seedTwoCells() {
        val mine = grid.cellOf(seoul)
        val other = grid.cellOf(LatLngPoint(37.5679, 126.9780))
        territory.cells.value = listOf(
            Cell(mine, "me", 0, 0, grid.regionOf(mine)),
            Cell(other, "u2", 4, 0, grid.regionOf(other)),
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
    fun `줌 14 미만이면 구독하지 않고 isZoomedOut`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            vm.onEvent(MapEvent.CameraIdle(seoul, zoom = 13.9f, byUser = false))
            val state = awaitItemUntil { it.isZoomedOut }
            assertTrue(state.cells.isEmpty())
            assertTrue(territory.requestedRegions.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `줌이 정확히 14 면 구독한다`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            vm.onEvent(MapEvent.CameraIdle(seoul, zoom = 14f, byUser = false))
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
            tracking.onWalkStarted()
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
}

/** turbine 보조: 조건을 만족하는 첫 아이템까지 소비한다. */
private suspend fun <T> ReceiveTurbine<T>.awaitItemUntil(predicate: (T) -> Boolean): T {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}
