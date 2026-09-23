package com.jaychoi.eattheland.feature.onboarding

import com.jaychoi.eattheland.core.domain.ValidateNicknameUseCase
import com.jaychoi.eattheland.core.model.PlayerError
import com.jaychoi.eattheland.core.testing.FakePlayerRepository
import com.jaychoi.eattheland.core.testing.MainDispatcherRule
import com.jaychoi.eattheland.feature.onboarding.ui.OnboardingEvent
import com.jaychoi.eattheland.feature.onboarding.ui.OnboardingStep
import com.jaychoi.eattheland.feature.onboarding.ui.OnboardingViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakePlayerRepository()

    private fun viewModel() = OnboardingViewModel(repository, ValidateNicknameUseCase())

    @Test
    fun `initialize 는 익명 로그인을 시도하고 Intro 에 머문다`() = runTest {
        val vm = viewModel()
        vm.initialize()
        assertEquals(OnboardingStep.Intro, vm.uiState.value.step)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `로그인 실패는 error 로 표시되고 Retry 로 재시도한다`() = runTest {
        repository.signInError = PlayerError.Network
        val vm = viewModel()
        vm.initialize()
        assertEquals(PlayerError.Network, vm.uiState.value.error)
        repository.signInError = null
        vm.onEvent(OnboardingEvent.Retry)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `Next 로 Intro → Permission, 권한 거부여도 Nickname 으로 간다`() = runTest {
        val vm = viewModel()
        vm.initialize()
        vm.onEvent(OnboardingEvent.Next)
        assertEquals(OnboardingStep.Permission, vm.uiState.value.step)
        vm.onEvent(OnboardingEvent.PermissionResult(locationGranted = false))
        assertEquals(OnboardingStep.Nickname, vm.uiState.value.step)
    }

    @Test
    fun `닉네임 유효성은 입력마다 갱신된다`() = runTest {
        val vm = viewModel()
        vm.onEvent(OnboardingEvent.NicknameChanged("a"))
        assertFalse(vm.uiState.value.isNicknameValid)
        vm.onEvent(OnboardingEvent.NicknameChanged("땅주인"))
        assertTrue(vm.uiState.value.isNicknameValid)
    }

    @Test
    fun `Submit 성공 시 completed=true, Consumed 로 되돌린다`() = runTest {
        val vm = viewModel()
        vm.onEvent(OnboardingEvent.NicknameChanged("땅주인"))
        vm.onEvent(OnboardingEvent.Submit)
        assertTrue(vm.uiState.value.completed)
        assertEquals(listOf("땅주인"), repository.setNicknameCalls)
        vm.onEvent(OnboardingEvent.CompletedConsumed)
        assertFalse(vm.uiState.value.completed)
    }

    @Test
    fun `Submit 중복 닉네임은 NicknameTaken 에러`() = runTest {
        repository.setNicknameError = PlayerError.NicknameTaken
        val vm = viewModel()
        vm.onEvent(OnboardingEvent.NicknameChanged("땅주인"))
        vm.onEvent(OnboardingEvent.Submit)
        assertEquals(PlayerError.NicknameTaken, vm.uiState.value.error)
        assertFalse(vm.uiState.value.completed)
    }

    @Test
    fun `유효하지 않은 닉네임으로는 Submit 이 무시된다`() = runTest {
        val vm = viewModel()
        vm.onEvent(OnboardingEvent.NicknameChanged("a"))
        vm.onEvent(OnboardingEvent.Submit)
        assertTrue(repository.setNicknameCalls.isEmpty())
    }
}
