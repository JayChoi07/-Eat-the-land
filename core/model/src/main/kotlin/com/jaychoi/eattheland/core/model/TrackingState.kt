package com.jaychoi.eattheland.core.model

/** 산책 추적 상태. 서비스가 쓰고 지도 화면이 읽는다. capturedCount 는 이번 산책에서 잡은(큐 포함) 셀 수. */
data class TrackingState(
    val isTracking: Boolean = false,
    val capturedCount: Int = 0,
    val lastPoint: LatLngPoint? = null,
    val isGpsWeak: Boolean = false,
)
