package com.jaychoi.eattheland.core.domain

import com.jaychoi.eattheland.core.model.LatLngPoint
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_METERS = 6_371_000.0

/**
 * 두 점 사이 지표 거리(하버사인). 셀 폭 50 m 수준에서 구면 근사 오차는 무시할 만하다.
 * :app WalkTracker 가 거리 누적에 쓴다(스펙 C 결정 4).
 */
fun distanceMeters(a: LatLngPoint, b: LatLngPoint): Double {
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLng = Math.toRadians(b.lng - a.lng)
    val h = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).pow(2)
    return 2 * EARTH_RADIUS_METERS * asin(sqrt(h))
}
