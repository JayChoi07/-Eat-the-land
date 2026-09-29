package com.jaychoi.eattheland.core.network

/** `walks/{uid}/items/{autoId}` 한 건(스펙 C §8). 시각은 클라 밀리초, createdAt 은 데이터소스가 서버 시각으로 찍는다. */
data class WalkDto(
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val cells: Int,
    val meters: Int,
)

interface WalkDataSource {
    /** 실패는 DataSourceException. 오프라인이면 SDK 가 쓰기를 보관하고 응답하지 않는다 — 호출자가 시간을 정한다. */
    suspend fun create(uid: String, walk: WalkDto)
}
