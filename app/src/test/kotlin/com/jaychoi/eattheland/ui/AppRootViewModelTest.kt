package com.jaychoi.eattheland.ui

import app.cash.turbine.test
import com.jaychoi.eattheland.core.model.Player
import com.jaychoi.eattheland.core.testing.FakePlayerRepository
import com.jaychoi.eattheland.core.testing.MainDispatcherRule
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppRootViewModelTest {
    // stateIn 의 초기값(isLoading=true)이 구독자에게 먼저 보이는지가 검증 대상이라 Unconfined 가 아니라
    // Standard 디스패처를 쓴다 — Unconfined 면 공유 코루틴이 구독 등록 중에 값을 덮어 초기값을 못 본다.
    private val scheduler = TestCoroutineScheduler()

    @get:Rule val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher(scheduler))

    private val repository = FakePlayerRepository()

    @Test
    fun `첫 값 전에는 isLoading, 프로필 없으면 hasProfile=false, 생기면 true`() = runTest(scheduler) {
        val vm = AppRootViewModel(repository)
        vm.uiState.test {
            assertEquals(AppRootUiState(isLoading = true), awaitItem())
            assertEquals(AppRootUiState(isLoading = false, hasProfile = false), awaitItem())
            repository.playerFlow.value = Player("u", "n", 0, 0)
            assertEquals(AppRootUiState(isLoading = false, hasProfile = true), awaitItem())
        }
    }
}
