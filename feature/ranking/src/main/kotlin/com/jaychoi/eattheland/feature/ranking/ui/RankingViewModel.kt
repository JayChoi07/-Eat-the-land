package com.jaychoi.eattheland.feature.ranking.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaychoi.eattheland.core.data.PlayerRepository
import com.jaychoi.eattheland.core.data.ranking.RankingRepository
import com.jaychoi.eattheland.core.model.RankingLoad
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * R-12-02 해당 0개 → MVVM-UDF. 읽기는 Route 의 initialize 로 시작한다(init 비동기 금지, R-12-07).
 * 플레이어(내 uid·닉네임)도 initialize 에서 수집해 같은 상태에 써 넣는다 — 테스트가 수집 없이
 * `uiState.value` 를 읽는다.
 */
@HiltViewModel
class RankingViewModel @Inject constructor(
    private val ranking: RankingRepository,
    private val players: PlayerRepository,
) : ViewModel() {

    private val local = MutableStateFlow(RankingUiState())
    private var initialized = false

    val uiState: StateFlow<RankingUiState> = local.asStateFlow()

    fun initialize() {
        if (initialized) return
        initialized = true
        viewModelScope.launch {
            players.currentPlayer.collect { player ->
                local.update {
                    it.copy(myUid = player?.uid, myNickname = player?.nickname.orEmpty())
                }
            }
        }
        load(force = false)
    }

    fun onEvent(event: RankingEvent) {
        when (event) {
            RankingEvent.Refresh -> load(force = true)
            RankingEvent.ErrorShown -> local.update { it.copy(error = null) }
        }
    }

    private fun load(force: Boolean) {
        viewModelScope.launch {
            local.update { it.copy(isRefreshing = force, error = null) }
            val result = ranking.load(force)
            local.update { state ->
                when (result) {
                    is RankingLoad.Success -> state.copy(
                        isLoading = false,
                        isRefreshing = false,
                        entries = result.ranking.entries,
                        me = result.ranking.me,
                    )

                    is RankingLoad.Failure -> state.copy(
                        isLoading = false,
                        isRefreshing = false,
                        entries = result.cached?.entries ?: state.entries,
                        me = result.cached?.me ?: state.me,
                        error = result.error,
                    )
                }
            }
        }
    }
}
