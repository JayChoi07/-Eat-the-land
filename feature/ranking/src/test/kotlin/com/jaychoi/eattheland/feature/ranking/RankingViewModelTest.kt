package com.jaychoi.eattheland.feature.ranking

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.jaychoi.eattheland.core.model.MyRank
import com.jaychoi.eattheland.core.model.Player
import com.jaychoi.eattheland.core.model.RankEntry
import com.jaychoi.eattheland.core.model.Ranking
import com.jaychoi.eattheland.core.model.RankingError
import com.jaychoi.eattheland.core.model.RankingLoad
import com.jaychoi.eattheland.core.testing.FakePlayerRepository
import com.jaychoi.eattheland.core.testing.FakeRankingRepository
import com.jaychoi.eattheland.core.testing.MainDispatcherRule
import com.jaychoi.eattheland.feature.ranking.ui.RankingEvent
import com.jaychoi.eattheland.feature.ranking.ui.RankingViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class RankingViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val ranking = FakeRankingRepository()
    private val players = FakePlayerRepository()
    private val top = Ranking(
        entries = listOf(RankEntry(1, "a", "A", 1, 10), RankEntry(2, "me", "나", 0, 7)),
        me = MyRank(2, 7),
    )

    private fun viewModel(): RankingViewModel {
        players.playerFlow.value = Player("me", "나", 0, 7)
        return RankingViewModel(ranking, players)
    }

    @Test
    fun `initialize 가 캐시 우선으로 읽어 목록·내 순위·내 uid 를 보인다`() = runTest {
        ranking.result = RankingLoad.Success(top)
        val vm = viewModel()
        vm.initialize()
        vm.uiState.test {
            val state = awaitItemUntil { it.entries.isNotEmpty() }
            assertEquals(listOf(false), ranking.loadCalls)
            assertEquals(MyRank(2, 7), state.me)
            assertEquals("me", state.myUid)
            assertFalse(state.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Refresh 는 force 로 읽고 isRefreshing 을 내린다`() = runTest {
        ranking.result = RankingLoad.Success(top)
        val vm = viewModel()
        vm.initialize()
        vm.onEvent(RankingEvent.Refresh)
        assertEquals(listOf(false, true), ranking.loadCalls)
        assertFalse(vm.uiState.value.isRefreshing)
    }

    @Test
    fun `실패는 캐시 목록을 유지하고 에러만 올린다, ErrorShown 으로 지운다`() = runTest {
        ranking.result = RankingLoad.Failure(RankingError.Offline, cached = top)
        val vm = viewModel()
        vm.initialize()
        assertEquals(2, vm.uiState.value.entries.size)
        assertEquals(RankingError.Offline, vm.uiState.value.error)
        vm.onEvent(RankingEvent.ErrorShown)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `캐시 없는 실패는 빈 목록과 에러`() = runTest {
        ranking.result = RankingLoad.Failure(RankingError.Unknown, cached = null)
        val vm = viewModel()
        vm.initialize()
        assertEquals(0, vm.uiState.value.entries.size)
        assertEquals(RankingError.Unknown, vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `initialize 는 한 번만 읽는다`() = runTest {
        ranking.result = RankingLoad.Success(top)
        val vm = viewModel()
        vm.initialize()
        vm.initialize()
        assertEquals(1, ranking.loadCalls.size)
    }
}

private suspend fun <T> ReceiveTurbine<T>.awaitItemUntil(predicate: (T) -> Boolean): T {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}
