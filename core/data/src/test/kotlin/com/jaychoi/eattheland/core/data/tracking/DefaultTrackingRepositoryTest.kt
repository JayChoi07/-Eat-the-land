package com.jaychoi.eattheland.core.data.tracking

import com.jaychoi.eattheland.core.model.LatLngPoint
import com.jaychoi.eattheland.core.model.TrackingState
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultTrackingRepositoryTest {
    private val repo = DefaultTrackingRepository()
    private val p = LatLngPoint(37.5665, 126.9780)

    @Test
    fun `시작하면 isTracking, 위치·캡처가 누적되고, 끝내면 카운트만 남는다`() {
        assertEquals(TrackingState(), repo.state.value)
        repo.onWalkStarted()
        repo.onLocation(p, isGpsWeak = false)
        repo.onCaptured()
        repo.onCaptured()
        assertEquals(
            TrackingState(isTracking = true, capturedCount = 2, lastPoint = p),
            repo.state.value,
        )
        repo.onLocation(null, isGpsWeak = true)
        assertEquals(p, repo.state.value.lastPoint) // 위치가 없어도 마지막 점은 유지
        assertEquals(true, repo.state.value.isGpsWeak)
        repo.onWalkStopped()
        assertEquals(
            TrackingState(isTracking = false, capturedCount = 2, lastPoint = p, isGpsWeak = false),
            repo.state.value,
        )
    }

    @Test
    fun `다시 시작하면 카운트가 0 부터`() {
        repo.onWalkStarted()
        repo.onCaptured()
        repo.onWalkStopped()
        repo.onWalkStarted()
        assertEquals(0, repo.state.value.capturedCount)
    }
}
