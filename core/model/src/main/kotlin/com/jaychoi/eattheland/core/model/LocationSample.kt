package com.jaychoi.eattheland.core.model

/**
 * 위치 한 점. speedMps 는 제공자가 못 줄 수 있어 null 허용(첫 fix·정지 상태).
 * elapsedMillis 는 부팅 후 경과 시간 — 벽시계가 아니라서 기기 시각이 바뀌어도 속도 계산이 흔들리지 않는다.
 * 밟은 시각(walkedAt)은 Repository 가 Clock 으로 따로 찍는다.
 */
data class LocationSample(
    val point: LatLngPoint,
    val accuracyMeters: Float,
    val speedMps: Float?,
    val elapsedMillis: Long,
    val isMock: Boolean,
)
