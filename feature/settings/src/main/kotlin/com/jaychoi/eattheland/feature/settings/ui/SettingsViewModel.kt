package com.jaychoi.eattheland.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaychoi.eattheland.core.data.PlayerRepository
import com.jaychoi.eattheland.core.domain.ValidateNicknameUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** R-12-02 매트릭스 해당 0개 → MVVM-UDF. 플레이어는 스트림, 나머지는 화면 로컬 상태를 combine 한다. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val players: PlayerRepository,
    private val validateNickname: ValidateNicknameUseCase,
) : ViewModel() {

    private val local = MutableStateFlow(SettingsUiState())

    val uiState: StateFlow<SettingsUiState> = combine(players.currentPlayer, local) { player, l ->
        l.copy(player = player)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

    fun onEvent(event: SettingsEvent) {
        when (event) {
            SettingsEvent.EditNickname -> local.update {
                val current = uiState.value.player?.nickname.orEmpty()
                it.copy(
                    isEditingNickname = true,
                    nicknameInput = current,
                    isNicknameValid = validateNickname(current),
                    error = null,
                )
            }

            is SettingsEvent.NicknameChanged -> local.update {
                it.copy(
                    nicknameInput = event.value,
                    isNicknameValid = validateNickname(event.value),
                    error = null,
                )
            }

            SettingsEvent.SaveNickname -> saveNickname()

            SettingsEvent.CancelEdit -> local.update {
                it.copy(
                    isEditingNickname = false,
                    nicknameInput = "",
                    isNicknameValid = false,
                    error = null,
                )
            }

            is SettingsEvent.PermissionsRead -> local.update {
                it.copy(locationGranted = event.location, notificationGranted = event.notification)
            }

            SettingsEvent.DeleteRequested -> local.update {
                it.copy(showDeleteConfirm = true, error = null)
            }

            SettingsEvent.DeleteCancelled -> local.update { it.copy(showDeleteConfirm = false) }

            SettingsEvent.DeleteConfirmed -> deleteAccount()

            SettingsEvent.DeletedConsumed -> local.update { it.copy(deleted = false) }

            SettingsEvent.ErrorShown -> local.update { it.copy(error = null) }
        }
    }

    private fun saveNickname() {
        val state = local.value
        if (!state.isNicknameValid || state.isSaving) return
        viewModelScope.launch {
            local.update { it.copy(isSaving = true, error = null) }
            val error = players.setNickname(state.nicknameInput)
            local.update {
                if (error == null) {
                    it.copy(isSaving = false, isEditingNickname = false, nicknameInput = "")
                } else {
                    it.copy(isSaving = false, error = error)
                }
            }
        }
    }

    private fun deleteAccount() {
        if (local.value.isDeleting) return
        viewModelScope.launch {
            local.update { it.copy(isDeleting = true, showDeleteConfirm = false, error = null) }
            val error = players.deleteAccount()
            local.update { it.copy(isDeleting = false, error = error, deleted = error == null) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
