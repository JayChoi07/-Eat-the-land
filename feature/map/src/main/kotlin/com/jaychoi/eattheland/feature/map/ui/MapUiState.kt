package com.jaychoi.eattheland.feature.map.ui

import com.jaychoi.eattheland.core.model.CellId
import com.jaychoi.eattheland.core.model.LatLngPoint
import com.jaychoi.eattheland.core.model.Player

/** 이 줌 미만에서는 셀 리스너를 걸지 않고 안내 문구를 띄운다(Firestore read 절약). 실기기에서 카카오 줌 스케일 확인 후 조정. */
const val MIN_OVERLAY_ZOOM = 14f

data class CellPolygon(
    val id: CellId,
    val points: List<LatLngPoint>,
    /** null = 내 셀 */
    val colorIndex: Int?,
)

data class MapUiState(
    val player: Player? = null,
    val cells: List<CellPolygon> = emptyList(),
    val isZoomedOut: Boolean = false,
)

sealed interface MapEvent {
    data class CameraIdle(val center: LatLngPoint, val zoom: Float) : MapEvent
}
