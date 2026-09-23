package com.jaychoi.eattheland.feature.map

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.jaychoi.eattheland.core.model.Cell
import com.jaychoi.eattheland.core.model.LatLngPoint
import com.jaychoi.eattheland.core.model.Player
import com.jaychoi.eattheland.core.testing.FakeHexGrid
import com.jaychoi.eattheland.core.testing.FakePlayerRepository
import com.jaychoi.eattheland.core.testing.FakeTerritoryRepository
import com.jaychoi.eattheland.core.testing.MainDispatcherRule
import com.jaychoi.eattheland.feature.map.ui.MapEvent
import com.jaychoi.eattheland.feature.map.ui.MapViewModel
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
    private val seoul = LatLngPoint(37.5661, 126.9780)

    private fun viewModel() = MapViewModel(territory, players, grid)

    @Test
    fun `카메라가 멈추면 그 region 의 셀을 구독하고 폴리곤으로 바꾼다`() = runTest {
        val me = Player("me", "나", 0, 3)
        players.playerFlow.value = me
        val mine = grid.cellOf(seoul)
        val other = grid.cellOf(LatLngPoint(37.5679, 126.9780))
        territory.cells.value = listOf(
            Cell(mine, "me", 0, 0, grid.regionOf(mine)),
            Cell(other, "u2", 4, 0, grid.regionOf(other)),
        )
        val vm = viewModel()
        vm.uiState.test {
            vm.initialize()
            vm.onEvent(MapEvent.CameraIdle(seoul, zoom = 16f))
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
        vm.initialize()
        vm.onEvent(MapEvent.CameraIdle(seoul, zoom = 13.9f))
        assertTrue(vm.uiState.value.isZoomedOut)
        assertTrue(vm.uiState.value.cells.isEmpty())
        assertTrue(territory.requestedRegions.isEmpty())
    }

    @Test
    fun `같은 region 안에서 카메라가 움직이면 재구독하지 않는다`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            vm.initialize()
            vm.onEvent(MapEvent.CameraIdle(seoul, zoom = 16f))
            vm.onEvent(MapEvent.CameraIdle(LatLngPoint(37.5662, 126.9781), zoom = 17f))
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, territory.requestedRegions.size)
    }
}

/** turbine 보조: 조건을 만족하는 첫 아이템까지 소비한다. */
private suspend fun <T> ReceiveTurbine<T>.awaitItemUntil(predicate: (T) -> Boolean): T {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}
