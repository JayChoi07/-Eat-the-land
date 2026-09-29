package com.jaychoi.eattheland.core.network

import kotlinx.coroutines.flow.Flow

enum class CaptureOutcome { Captured, AlreadyMine }

/** 캡처 쓰기 한 건. walkedAtMillis 는 클라가 밟은 시각(온라인은 지금, 재전송은 큐에 넣은 시각 — 스펙 §4 v3). */
data class CaptureRequest(
    val cellId: String,
    val region: String,
    val uid: String,
    val color: Int,
    val walkedAtMillis: Long,
)

interface CellDataSource {
    /** `cells where region in regions` 실시간 스냅샷. 문서 ID → DTO. regions 가 비면 빈 맵 한 번. */
    fun observe(regions: Set<String>): Flow<Map<String, CellDto>>

    /**
     * 스펙 §4 capture 트랜잭션. 셀을 내 소유로 쓰고 나 +1, 이전 소유자 −1.
     * 실패는 DataSourceException(Offline 이면 호출자가 큐에 넣는다).
     */
    suspend fun capture(request: CaptureRequest): CaptureOutcome
}
