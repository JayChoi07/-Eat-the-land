package com.jaychoi.eattheland.core.model

/** 위치 한 점. speedMps 는 제공자가 못 줄 수 있어 null 허용(첫 fix·정지 상태). */
data class LocationSample(
    val point: LatLngPoint,
    val accuracyMeters: Float,
    val speedMps: Float?,
    val timeMillis: Long,
    val isMock: Boolean,
)
