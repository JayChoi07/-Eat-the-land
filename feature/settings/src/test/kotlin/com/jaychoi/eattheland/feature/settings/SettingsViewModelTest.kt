package com.jaychoi.eattheland.feature.settings

import app.cash.turbine.test
import com.jaychoi.eattheland.core.domain.ValidateNicknameUseCase
import com.jaychoi.eattheland.core.model.Player
import com.jaychoi.eattheland.core.model.PlayerError
import com.jaychoi.eattheland.core.testing.FakePlayerRepository
import com.jaychoi.eattheland.core.testing.MainDispatcherRule
import com.jaychoi.eattheland.feature.settings.ui.SettingsEvent
import com.jaychoi.eattheland.feature.settings.ui.SettingsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val players = FakePlayerRepository()
    private val me = Player("u1", "땅주인", 2, 7)

    // uiState 는 WhileSubscribed 라 구독자가 없으면 .value 가 초기값에 머문다 — 화면처럼 계속 수집한다.
    private fun TestScope.viewModel(): SettingsViewModel {
        players.playerFlow.value = me
        val vm = SettingsViewModel(players, ValidateNicknameUseCase())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    @Test
    fun `플레이어를 보여주고, 편집 시작은 현재 닉네임을 입력에 넣는다`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            assertEquals(me, awaitItem().player)
            vm.onEvent(SettingsEvent.EditNickname)
            val editing = awaitItem()
            assertTrue(editing.isEditingNickname)
            assertEquals("땅주인", editing.nicknameInput)
            assertTrue(editing.isNicknameValid)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `저장은 setNickname 을 부르고 편집을 닫는다`() = runTest {
        val vm = viewModel()
        vm.onEvent(SettingsEvent.EditNickname)
        vm.onEvent(SettingsEvent.NicknameChanged("걷는사람"))
        vm.onEvent(SettingsEvent.SaveNickname)
        assertEquals(listOf("걷는사람"), players.setNicknameCalls)
        assertFalse(vm.uiState.value.isEditingNickname)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `중복 닉네임은 에러를 보이고 편집을 유지한다`() = runTest {
        players.setNicknameError = PlayerError.NicknameTaken
        val vm = viewModel()
        vm.onEvent(SettingsEvent.EditNickname)
        vm.onEvent(SettingsEvent.NicknameChanged("산책왕"))
        vm.onEvent(SettingsEvent.SaveNickname)
        assertEquals(PlayerError.NicknameTaken, vm.uiState.value.error)
        assertTrue(vm.uiState.value.isEditingNickname)
    }

    @Test
    fun `형식이 틀리면 저장하지 않는다`() = runTest {
        val vm = viewModel()
        vm.onEvent(SettingsEvent.EditNickname)
        vm.onEvent(SettingsEvent.NicknameChanged("a"))
        vm.onEvent(SettingsEvent.SaveNickname)
        assertTrue(players.setNicknameCalls.isEmpty())
        assertFalse(vm.uiState.value.isNicknameValid)
    }

    @Test
    fun `편집 취소는 입력을 버리고 프로필은 그대로`() = runTest {
        val vm = viewModel()
        vm.onEvent(SettingsEvent.EditNickname)
        vm.onEvent(SettingsEvent.NicknameChanged("버릴이름"))
        vm.onEvent(SettingsEvent.CancelEdit)
        assertFalse(vm.uiState.value.isEditingNickname)
        assertEquals("", vm.uiState.value.nicknameInput)
        assertTrue(players.setNicknameCalls.isEmpty())
        assertEquals(me, vm.uiState.value.player)
    }

    @Test
    fun `삭제는 확인을 거쳐 deleteAccount 를 부르고 deleted 를 올린다`() = runTest {
        val vm = viewModel()
        vm.onEvent(SettingsEvent.DeleteRequested)
        assertTrue(vm.uiState.value.showDeleteConfirm)
        assertEquals(0, players.deleteCalls)
        vm.onEvent(SettingsEvent.DeleteConfirmed)
        assertEquals(1, players.deleteCalls)
        assertTrue(vm.uiState.value.deleted)
        assertFalse(vm.uiState.value.showDeleteConfirm)
        vm.onEvent(SettingsEvent.DeletedConsumed)
        assertFalse(vm.uiState.value.deleted)
    }

    @Test
    fun `삭제 실패는 에러를 보이고 deleted 를 올리지 않는다`() = runTest {
        players.deleteAccountError = PlayerError.Network
        val vm = viewModel()
        vm.onEvent(SettingsEvent.DeleteRequested)
        vm.onEvent(SettingsEvent.DeleteConfirmed)
        assertEquals(PlayerError.Network, vm.uiState.value.error)
        assertFalse(vm.uiState.value.deleted)
        assertFalse(vm.uiState.value.isDeleting)
    }

    @Test
    fun `권한 상태는 Route 가 넣어 준다`() = runTest {
        val vm = viewModel()
        vm.onEvent(SettingsEvent.PermissionsRead(location = true, notification = false))
        assertTrue(vm.uiState.value.locationGranted)
        assertFalse(vm.uiState.value.notificationGranted)
    }
}
