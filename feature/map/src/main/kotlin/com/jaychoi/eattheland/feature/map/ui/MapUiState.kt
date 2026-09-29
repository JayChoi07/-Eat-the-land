package com.jaychoi.eattheland.feature.map.ui

import com.jaychoi.eattheland.core.model.CellId
import com.jaychoi.eattheland.core.model.LatLngPoint
import com.jaychoi.eattheland.core.model.Player

/** 이 줌 미만에서는 셀 리스너를 걸지 않고 안내 문구를 띄운다(Firestore read 절약). 실기기에서 카카오 줌 스케일 확인(2026-09-29). */
const val MIN_OVERLAY_ZOOM = 14f

data class CellPolygon(
    val id: CellId,
    val points: List<LatLngPoint>,
    /** null = 내 셀 */
    val colorIndex: Int?,
)

/** 마지막으로 카메라가 멈춘 곳. 회전으로 MapView 가 다시 만들어질 때 시작 위치로 쓴다. */
data class CameraSnapshot(val center: LatLngPoint, val zoom: Int)

data class MapUiState(
    val player: Player? = null,
    val cells: List<CellPolygon> = emptyList(),
    val isZoomedOut: Boolean = false,
    /** 카카오맵 onMapError. 다시 시도가 attempt 를 올리면 Route 가 MapView 를 새로 만든다. */
    val mapLoadFailed: Boolean = false,
    val mapAttempt: Int = 0,
    val camera: CameraSnapshot? = null,
)

sealed interface MapEvent {
    data class CameraIdle(val center: LatLngPoint, val zoom: Float) : MapEvent

    data object MapLoadFailed : MapEvent

    data object RetryMap : MapEvent
}
