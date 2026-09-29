package com.jaychoi.eattheland.tracking

import com.jaychoi.eattheland.core.domain.CaptureCellUseCase
import com.jaychoi.eattheland.core.model.CaptureResult
import com.jaychoi.eattheland.core.model.LatLngPoint
import com.jaychoi.eattheland.core.model.LocationSample
import com.jaychoi.eattheland.core.model.LocationUpdate
import com.jaychoi.eattheland.core.testing.FakeHexGrid
import com.jaychoi.eattheland.core.testing.FakeLocationRepository
import com.jaychoi.eattheland.core.testing.FakeTerritoryRepository
import com.jaychoi.eattheland.core.testing.FakeTrackingRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WalkTrackerTest {
    private val locations = FakeLocationRepository()
    private val territory = FakeTerritoryRepository()
    private val tracking = FakeTrackingRepository()
    private val grid = FakeHexGrid()
    private val tracker = WalkTracker(locations, territory, tracking, CaptureCellUseCase(), grid)

    private val a = LatLngPoint(37.5661, 126.9780)
    private val b = LatLngPoint(37.5679, 126.9780)

    private fun fix(p: LatLngPoint, accuracy: Float = 10f, speed: Float? = 1.2f) =
        LocationUpdate.Fix(LocationSample(p, accuracy, speed, timeMillis = 0L, isMock = false))

    private fun TestScope.start(): Job =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { tracker.run() }

    @Test
    fun `시작하면 큐를 비우고 추적 상태가 되며, 새 셀마다 캡처하고 센다`() = runTest {
        start()
        assertTrue(tracking.state.value.isTracking)
        assertEquals(1, territory.flushCalls)
        locations.updates.emit(fix(a))
        locations.updates.emit(fix(a)) // 같은 셀 — 캡처 안 함
        locations.updates.emit(fix(b))
        assertEquals(listOf(grid.cellOf(a), grid.cellOf(b)), territory.captureCalls)
        assertEquals(2, tracking.state.value.capturedCount)
        assertEquals(b, tracking.state.value.lastPoint)
    }

    @Test
    fun `큐에 들어간 캡처도 세고, 이미 내 셀은 세지 않는다`() = runTest {
        start()
        territory.captureResult = CaptureResult.Queued
        locations.updates.emit(fix(a))
        territory.captureResult = CaptureResult.AlreadyMine
        locations.updates.emit(fix(b))
        assertEquals(1, tracking.state.value.capturedCount)
    }

    @Test
    fun `실패한 셀은 다음 위치에서 다시 시도한다`() = runTest {
        start()
        territory.captureResult = CaptureResult.Failed(null)
        locations.updates.emit(fix(a))
        territory.captureResult = CaptureResult.Captured
        locations.updates.emit(fix(a))
        assertEquals(listOf(grid.cellOf(a), grid.cellOf(a)), territory.captureCalls)
    }

    @Test
    fun `정확도가 나쁘면 캡처하지 않고 GPS 약함, 좋아지면 해제`() = runTest {
        start()
        locations.updates.emit(fix(a, accuracy = 80f))
        assertTrue(tracking.state.value.isGpsWeak)
        assertTrue(territory.captureCalls.isEmpty())
        locations.updates.emit(fix(a, accuracy = 10f))
        assertEquals(false, tracking.state.value.isGpsWeak)
    }

    @Test
    fun `위치를 못 구하면(권한 회수·GPS 꺼짐) 죽지 않고 GPS 약함`() = runTest {
        start()
        locations.updates.emit(LocationUpdate.Unavailable)
        assertTrue(tracking.state.value.isGpsWeak)
        assertTrue(tracking.state.value.isTracking)
    }

    @Test
    fun `취소되면 추적 종료 상태로 돌아간다`() = runTest {
        val job = start()
        job.cancel()
        runCurrent()
        assertEquals(false, tracking.state.value.isTracking)
    }
}
