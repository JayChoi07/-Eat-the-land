package com.jaychoi.eattheland.feature.onboarding.ui

import com.jaychoi.eattheland.core.model.PlayerError

enum class OnboardingStep { Intro, Permission, Nickname }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Intro,
    val nickname: String = "",
    val isNicknameValid: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: PlayerError? = null,
    /** 닉네임 저장 완료. UI 가 onCompleted 를 부른 뒤 Consumed 이벤트로 되돌린다 (R-12-03). */
    val completed: Boolean = false,
)

sealed interface OnboardingEvent {
    data object Next : OnboardingEvent

    data object Back : OnboardingEvent

    data class PermissionResult(val locationGranted: Boolean) : OnboardingEvent

    data class NicknameChanged(val value: String) : OnboardingEvent

    data object Submit : OnboardingEvent

    data object Retry : OnboardingEvent

    data object CompletedConsumed : OnboardingEvent
}
