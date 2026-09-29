package com.jaychoi.eattheland.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaychoi.eattheland.core.data.PlayerRepository
import com.jaychoi.eattheland.core.data.tracking.TrackingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AppRootUiState(
    val isLoading: Boolean = true,
    val hasProfile: Boolean = false,
    /** 두 번 뒤로가기 안내 문구가 갈린다(산책은 알림에서 계속됨). */
    val isTracking: Boolean = false,
)

/** 시작 분기(온보딩/지도)는 Activity 가 아니라 루트 상태 홀더가 정한다 (R-18-06). */
@HiltViewModel
class AppRootViewModel @Inject constructor(
    players: PlayerRepository,
    tracking: TrackingRepository,
) : ViewModel() {
    val uiState: StateFlow<AppRootUiState> =
        combine(players.currentPlayer, tracking.state) { player, walk ->
            AppRootUiState(
                isLoading = false,
                hasProfile = player != null,
                isTracking = walk.isTracking,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppRootUiState())

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
