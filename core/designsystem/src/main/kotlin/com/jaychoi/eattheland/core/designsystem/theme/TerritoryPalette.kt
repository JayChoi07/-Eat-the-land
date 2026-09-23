package com.jaychoi.eattheland.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 유저별 영토 색 7종. 유저 `color` 인덱스(0..6)로 고른다. 내 셀은 항상 primary (스펙 §6).
 * `theme/` 의 공개 API 는 AppTheme 하나라는 R-18-10 을 벗어나므로 표준 준수 보고에 적는다 —
 * 지도 오버레이는 시맨틱 역할로 표현할 수 없는 "데이터 색"이라 별도 진입점이 필요하다.
 */
object TerritoryPalette {
    private val colors = listOf(
        Color(0xFFFF5C8A),
        Color(0xFFFFB020),
        Color(0xFF3BC9DB),
        Color(0xFF9B6BFF),
        Color(0xFFFF7A3D),
        Color(0xFFF472B6),
        Color(0xFF38BDF8),
    )

    val size: Int get() = colors.size

    /** index 가 null 이면 내 영토색(primary). */
    @Composable
    fun color(index: Int?): Color =
        if (index == null) MaterialTheme.colorScheme.primary else colors[index.mod(colors.size)]
}
