package com.jaychoi.eattheland.feature.map.ui

import com.jaychoi.eattheland.feature.map.model.Map
import com.jaychoi.eattheland.feature.map.model.MapError

/** 화면 상태. 모든 필드에 기본값. 불변 (R-12-01). 에러는 도메인 타입 그대로 담는다. */
data class MapUiState(
    val isLoading: Boolean = false,
    val data: Map? = null,
    val error: MapError? = null,
)

/** 사용자 이벤트. UI → ViewModel 단방향 (R-00-01). */
sealed interface MapEvent {
    data object Retry : MapEvent
}
