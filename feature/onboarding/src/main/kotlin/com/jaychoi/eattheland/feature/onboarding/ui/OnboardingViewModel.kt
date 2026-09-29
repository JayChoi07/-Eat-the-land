package com.jaychoi.eattheland.feature.onboarding.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaychoi.eattheland.core.data.PlayerRepository
import com.jaychoi.eattheland.core.domain.ValidateNicknameUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** R-12-02 매트릭스 해당 0개 → MVVM-UDF. */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val players: PlayerRepository,
    private val validateNickname: ValidateNicknameUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private var initialized = false

    fun initialize() {
        if (initialized) return
        initialized = true
        signIn()
    }

    fun onEvent(event: OnboardingEvent) {
        when (event) {
            OnboardingEvent.Next -> _uiState.update { it.copy(step = OnboardingStep.Permission) }

            OnboardingEvent.Back -> _uiState.update {
                it.copy(
                    step = when (it.step) {
                        OnboardingStep.Intro -> OnboardingStep.Intro
                        OnboardingStep.Permission -> OnboardingStep.Intro
                        OnboardingStep.Nickname -> OnboardingStep.Permission
                    },
                    error = null,
                )
            }

            is OnboardingEvent.PermissionResult -> _uiState.update {
                it.copy(step = OnboardingStep.Nickname)
            }

            is OnboardingEvent.NicknameChanged -> _uiState.update {
                it.copy(
                    nickname = event.value,
                    isNicknameValid = validateNickname(event.value),
                    error = null,
                )
            }

            OnboardingEvent.Submit -> submit()

            OnboardingEvent.Retry -> signIn()

            OnboardingEvent.CompletedConsumed -> _uiState.update { it.copy(completed = false) }
        }
    }

    private fun signIn() {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            val error = players.ensureSignedIn()
            _uiState.update { it.copy(error = error) }
        }
    }

    private fun submit() {
        val state = _uiState.value
        if (!state.isNicknameValid || state.isSubmitting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            val error = players.setNickname(state.nickname)
            _uiState.update {
                it.copy(isSubmitting = false, error = error, completed = error == null)
            }
        }
    }
}
