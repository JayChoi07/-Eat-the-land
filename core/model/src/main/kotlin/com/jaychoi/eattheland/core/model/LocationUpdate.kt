package com.jaychoi.eattheland.core.model

/** 위치 스트림 항목. Unavailable = 제공자가 위치를 못 구함(GPS 꺼짐·권한 회수). */
sealed interface LocationUpdate {
    data class Fix(val sample: LocationSample) : LocationUpdate

    data object Unavailable : LocationUpdate
}
