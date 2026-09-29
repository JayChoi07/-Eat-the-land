package com.jaychoi.eattheland.core.data.tracking

import com.jaychoi.eattheland.core.model.LatLngPoint
import com.jaychoi.eattheland.core.model.TrackingState
import kotlinx.coroutines.flow.StateFlow

/** 산책 추적 상태. 서비스가 쓰고(:app WalkTracker) 지도 화면이 읽는다. 프로세스 안에서만 산다. */
interface TrackingRepository {
    val state: StateFlow<TrackingState>

    /** 카운트·거리 0, 시작 시각 기록, 직전 요약 제거. */
    fun onWalkStarted(nowMillis: Long)

    /** isTracking=false 와 함께 이번 산책의 요약을 남긴다(시작이 없었으면 요약 없음). */
    fun onWalkStopped(nowMillis: Long)

    /** point 가 null 이면 위치를 못 구한 것 — 마지막 점은 그대로 두고 GPS 약함만 표시한다. */
    fun onLocation(point: LatLngPoint?, isGpsWeak: Boolean)

    fun onCaptured()

    /** 판정을 통과한 fix 사이 거리를 더한다(스펙 C 결정 4). */
    fun onDistance(meters: Double)

    /** 결과 시트를 닫았다. */
    fun onSummaryDismissed()
}
