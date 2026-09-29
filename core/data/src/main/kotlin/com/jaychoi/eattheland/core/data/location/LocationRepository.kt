package com.jaychoi.eattheland.core.data.location

import com.jaychoi.eattheland.core.model.LatLngPoint
import com.jaychoi.eattheland.core.model.LocationUpdate
import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    /** 산책용 연속 위치. 권한이 없거나 회수되면 Unavailable 을 내고 끝난다(예외 없음). */
    fun updates(): Flow<LocationUpdate>

    /** 지도를 열 때 한 번. 권한이 없거나 아직 없으면 null. */
    suspend fun lastKnown(): LatLngPoint?
}
