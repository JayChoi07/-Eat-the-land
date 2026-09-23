package com.jaychoi.eattheland.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaychoi.eattheland.core.data.PlayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class AppRootUiState(val isLoading: Boolean = true, val hasProfile: Boolean = false)

/** 시작 분기(온보딩/지도)는 Activity 가 아니라 루트 상태 홀더가 정한다 (R-18-06). */
@HiltViewModel
class AppRootViewModel @Inject constructor(players: PlayerRepository) : ViewModel() {
    val uiState: StateFlow<AppRootUiState> = players.currentPlayer
        .map { AppRootUiState(isLoading = false, hasProfile = it != null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppRootUiState())

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
