package com.jaychoi.eattheland.core.data.tracking

import com.jaychoi.eattheland.core.model.LatLngPoint
import com.jaychoi.eattheland.core.model.TrackingState
import com.jaychoi.eattheland.core.model.WalkSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultTrackingRepositoryTest {
    private val repo = DefaultTrackingRepository()
    private val p = LatLngPoint(37.5665, 126.9780)

    @Test
    fun `시작하면 isTracking·시작 시각, 위치·캡처·거리가 누적되고, 끝내면 요약이 남는다`() {
        assertEquals(TrackingState(), repo.state.value)
        repo.onWalkStarted(nowMillis = 1_000L)
        repo.onLocation(p, isGpsWeak = false)
        repo.onCaptured()
        repo.onCaptured()
        repo.onDistance(120.5)
        repo.onDistance(30.0)
        assertEquals(
            TrackingState(
                isTracking = true,
                capturedCount = 2,
                lastPoint = p,
                distanceMeters = 150.5,
                startedAtMillis = 1_000L,
            ),
            repo.state.value,
        )
        repo.onLocation(null, isGpsWeak = true)
        assertEquals(p, repo.state.value.lastPoint) // 위치가 없어도 마지막 점은 유지
        assertEquals(true, repo.state.value.isGpsWeak)
        repo.onWalkStopped(nowMillis = 61_000L)
        assertEquals(
            TrackingState(
                isTracking = false,
                capturedCount = 2,
                lastPoint = p,
                isGpsWeak = false,
                distanceMeters = 150.5,
                startedAtMillis = 1_000L,
                lastSummary = WalkSummary(
                    startedAtMillis = 1_000L,
                    endedAtMillis = 61_000L,
                    cells = 2,
                    meters = 150.5,
                ),
            ),
            repo.state.value,
        )
    }

    @Test
    fun `다시 시작하면 카운트·거리가 0 부터이고 lastSummary 가 지워진다`() {
        repo.onWalkStarted(nowMillis = 0L)
        repo.onCaptured()
        repo.onDistance(10.0)
        repo.onWalkStopped(nowMillis = 5_000L)
        repo.onWalkStarted(nowMillis = 9_000L)
        assertEquals(0, repo.state.value.capturedCount)
        assertEquals(0.0, repo.state.value.distanceMeters, 0.0)
        assertEquals(9_000L, repo.state.value.startedAtMillis)
        assertNull(repo.state.value.lastSummary)
    }

    @Test
    fun `요약을 닫으면 lastSummary 만 사라진다`() {
        repo.onWalkStarted(nowMillis = 0L)
        repo.onWalkStopped(nowMillis = 5_000L)
        repo.onSummaryDismissed()
        assertNull(repo.state.value.lastSummary)
        assertEquals(false, repo.state.value.isTracking)
    }

    @Test
    fun `시작 없이 끝내면(방어) 요약은 만들지 않는다`() {
        repo.onWalkStopped(nowMillis = 5_000L)
        assertNull(repo.state.value.lastSummary)
    }
}
