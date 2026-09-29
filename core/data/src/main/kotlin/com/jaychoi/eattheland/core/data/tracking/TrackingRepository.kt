package com.jaychoi.eattheland.core.data.tracking

import com.jaychoi.eattheland.core.model.LatLngPoint
import com.jaychoi.eattheland.core.model.TrackingState
import kotlinx.coroutines.flow.StateFlow

/** 산책 추적 상태. 서비스가 쓰고(:app WalkTracker) 지도 화면이 읽는다. 프로세스 안에서만 산다. */
interface TrackingRepository {
    val state: StateFlow<TrackingState>

    fun onWalkStarted()

    fun onWalkStopped()

    /** point 가 null 이면 위치를 못 구한 것 — 마지막 점은 그대로 두고 GPS 약함만 표시한다. */
    fun onLocation(point: LatLngPoint?, isGpsWeak: Boolean)

    fun onCaptured()
}
