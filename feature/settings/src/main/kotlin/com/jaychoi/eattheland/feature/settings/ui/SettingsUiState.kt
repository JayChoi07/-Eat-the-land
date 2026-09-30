package com.jaychoi.eattheland.feature.settings.ui

import com.jaychoi.eattheland.core.model.Player
import com.jaychoi.eattheland.core.model.PlayerError

data class SettingsUiState(
    val player: Player? = null,
    val isEditingNickname: Boolean = false,
    val nicknameInput: String = "",
    val isNicknameValid: Boolean = false,
    val isSaving: Boolean = false,
    /** null 은 아직 읽지 않음 — 읽기 전 프레임에 "거부됨" 을 보이지 않는다. */
    val locationGranted: Boolean? = null,
    val notificationGranted: Boolean? = null,
    val showDeleteConfirm: Boolean = false,
    val isDeleting: Boolean = false,
    /** 삭제 완료. Route 가 onDeleted 를 부른 뒤 DeletedConsumed 로 되돌린다 (R-12-03). */
    val deleted: Boolean = false,
    val error: PlayerError? = null,
)

sealed interface SettingsEvent {
    data object EditNickname : SettingsEvent

    data class NicknameChanged(val value: String) : SettingsEvent

    data object SaveNickname : SettingsEvent

    data object CancelEdit : SettingsEvent

    data class PermissionsRead(val location: Boolean, val notification: Boolean) : SettingsEvent

    data object DeleteRequested : SettingsEvent

    data object DeleteCancelled : SettingsEvent

    data object DeleteConfirmed : SettingsEvent

    data object DeletedConsumed : SettingsEvent

    data object ErrorShown : SettingsEvent
}
