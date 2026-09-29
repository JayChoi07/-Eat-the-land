package com.jaychoi.eattheland.core.model

/**
 * 산책 추적 상태. 서비스가 쓰고 지도 화면이 읽는다. capturedCount 는 이번 산책에서 잡은(큐 포함) 셀 수.
 * distanceMeters 는 판정을 통과한 fix 사이 거리의 합, startedAtMillis 는 벽시계 시작 시각(화면이 경과 시간을 그린다),
 * lastSummary 는 직전 산책의 결과 — 시트를 닫으면 null(스펙 C §8).
 */
data class TrackingState(
    val isTracking: Boolean = false,
    val capturedCount: Int = 0,
    val lastPoint: LatLngPoint? = null,
    val isGpsWeak: Boolean = false,
    val distanceMeters: Double = 0.0,
    val startedAtMillis: Long? = null,
    val lastSummary: WalkSummary? = null,
)
