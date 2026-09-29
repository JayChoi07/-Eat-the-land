package com.jaychoi.eattheland.feature.map.ui

import com.jaychoi.eattheland.core.model.CellId
import com.jaychoi.eattheland.core.model.LatLngPoint
import com.jaychoi.eattheland.core.model.Player
import com.jaychoi.eattheland.core.model.WalkSummary

/**
 * 이 줌 미만에서는 셀 리스너를 걸지 않고 안내 문구를 띄운다(Firestore read 절약).
 * region res 8 리스너 7개(≈ 2.6 km 폭)가 화면을 덮는 최소 줌 — 실기기 확인(2026-09-29).
 */
const val MIN_OVERLAY_ZOOM = 15f

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
    /** 산책 중이면 추적 위치, 아니면 지도를 열 때 읽은 마지막 위치. */
    val myLocation: LatLngPoint? = null,
    /** true 면 카메라가 myLocation 을 따라간다. 사용자가 지도를 움직이면 꺼진다. */
    val isFollowing: Boolean = true,
    val isTracking: Boolean = false,
    val walkCellCount: Int = 0,
    /** 이번 산책 거리(판정 통과 fix 사이 합). */
    val distanceMeters: Double = 0.0,
    /** 산책 시작 후 경과. 산책 중이 아니면 null. ViewModel 이 1초마다 갱신한다. */
    val elapsedMillis: Long? = null,
    val pendingCount: Int = 0,
    val isGpsWeak: Boolean = false,
    /** "산책 시작" 을 눌렀는데 위치 권한을 거부한 뒤. */
    val showPermissionNotice: Boolean = false,
    /** 직전 산책 결과. 산책 중이 아니고 아직 닫지 않았을 때만. */
    val summary: WalkSummary? = null,
)

sealed interface MapEvent {
    /** byUser = 손으로 움직임(GestureType ≠ Unknown). 프로그램 이동은 따라가기를 끄지 않는다. */
    data class CameraIdle(val center: LatLngPoint, val zoom: Float, val byUser: Boolean) : MapEvent

    data object MapLoadFailed : MapEvent

    data object RetryMap : MapEvent

    data object MyLocationClicked : MapEvent

    /** requested = 권한 대화상자를 띄운 결과. false 면 화면을 열며 확인만 한 것. */
    data class LocationPermission(val granted: Boolean, val requested: Boolean) : MapEvent

    data object PermissionNoticeDismissed : MapEvent

    /** 산책이 끝났다 — 추적 위치 대신 마지막 위치를 새로 읽는다. */
    data object WalkStopped : MapEvent

    /** 결과 시트를 닫았다(확인·바깥 탭·뒤로). */
    data object SummaryDismissed : MapEvent
}
