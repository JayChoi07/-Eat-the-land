package com.jaychoi.eattheland.core.testing

import com.jaychoi.eattheland.core.data.location.LocationRepository
import com.jaychoi.eattheland.core.model.LatLngPoint
import com.jaychoi.eattheland.core.model.LocationUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class FakeLocationRepository : LocationRepository {
    /** 테스트가 emit 한다. 구독자가 없을 때 낸 값은 버려진다(replay 0). */
    val updates = MutableSharedFlow<LocationUpdate>(extraBufferCapacity = 64)

    /** 설정하면 updates() 가 이 Flow 를 돌려준다(끝나는 스트림·예외 흉내). */
    var updatesOverride: Flow<LocationUpdate>? = null
    var lastKnownPoint: LatLngPoint? = null
    var lastKnownCalls = 0
        private set

    override fun updates(): Flow<LocationUpdate> = updatesOverride ?: updates

    override suspend fun lastKnown(): LatLngPoint? {
        lastKnownCalls++
        return lastKnownPoint
    }
}
