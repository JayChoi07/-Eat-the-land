package com.jaychoi.eattheland.feature.ranking.ui

import com.jaychoi.eattheland.core.model.MyRank
import com.jaychoi.eattheland.core.model.RankEntry
import com.jaychoi.eattheland.core.model.RankingError

data class RankingUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val entries: List<RankEntry> = emptyList(),
    val me: MyRank? = null,
    /** 내 줄 강조용. 로그인 전이면 null. */
    val myUid: String? = null,
    val myNickname: String = "",
    val error: RankingError? = null,
)

sealed interface RankingEvent {
    data object Refresh : RankingEvent

    data object ErrorShown : RankingEvent
}
