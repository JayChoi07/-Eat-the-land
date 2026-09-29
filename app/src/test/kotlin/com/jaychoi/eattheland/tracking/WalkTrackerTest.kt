package com.jaychoi.eattheland.tracking

import com.jaychoi.eattheland.core.common.Clock
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
import kotlinx.coroutines.flow.flowOf
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
    private val tracker =
        WalkTracker(locations, territory, tracking, CaptureCellUseCase(), grid, Clock { 0L })

    private val a = LatLngPoint(37.5661, 126.9780)
    private val b = LatLngPoint(37.5679, 126.9780) // a 에서 북쪽 약 200 m, 다른 셀

    private fun fix(p: LatLngPoint, accuracy: Float = 10f, speed: Float? = 1.2f, time: Long = 0L) =
        LocationUpdate.Fix(LocationSample(p, accuracy, speed, elapsedMillis = time, isMock = false))

    private fun TestScope.start(): Job =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { tracker.run() }

    private suspend fun emitAll(vararg fixes: LocationUpdate) {
        fixes.forEach { locations.updates.emit(it) }
    }

    @Test
    fun `시작하면 큐를 비우고 추적 상태가 되며, 같은 새 셀 fix 2번 연속마다 캡처하고 센다`() = runTest {
        start()
        assertTrue(tracking.state.value.isTracking)
        assertEquals(1, territory.flushCalls)
        emitAll(fix(a))
        assertTrue(territory.captureCalls.isEmpty()) // 첫 fix 는 후보
        emitAll(fix(a), fix(a)) // 두 번째에 캡처, 세 번째는 같은 셀
        emitAll(fix(b), fix(b))
        assertEquals(listOf(grid.cellOf(a), grid.cellOf(b)), territory.captureCalls)
        assertEquals(2, tracking.state.value.capturedCount)
        assertEquals(b, tracking.state.value.lastPoint)
    }

    @Test
    fun `오가는 튐(A B A B)은 캡처하지 않는다`() = runTest {
        start()
        emitAll(fix(a), fix(a)) // a 캡처
        emitAll(fix(b), fix(a), fix(b))
        assertEquals(listOf(grid.cellOf(a)), territory.captureCalls)
        emitAll(fix(b)) // 이제야 b 가 2연속
        assertEquals(listOf(grid.cellOf(a), grid.cellOf(b)), territory.captureCalls)
    }

    @Test
    fun `정확도 나쁜 fix 가 끼면 연속이 끊긴다`() = runTest {
        start()
        emitAll(fix(a), fix(a, accuracy = 80f), fix(a))
        assertTrue(territory.captureCalls.isEmpty())
        emitAll(fix(a))
        assertEquals(listOf(grid.cellOf(a)), territory.captureCalls)
    }

    @Test
    fun `속도 미상이면 직전 fix 와의 거리로 판정한다`() = runTest {
        start()
        // 200 m 를 5초에 → 너무 빠름. 그 뒤 같은 자리 5초 → 0 m/s 로 후보, 다음에 캡처
        emitAll(
            fix(a, speed = null, time = 0L),
            fix(b, speed = null, time = 5_000L),
            fix(b, speed = null, time = 10_000L),
        )
        assertTrue(territory.captureCalls.isEmpty())
        emitAll(fix(b, speed = null, time = 15_000L))
        assertEquals(listOf(grid.cellOf(b)), territory.captureCalls)
    }

    @Test
    fun `큐에 들어간 캡처도 세고, 이미 내 셀은 세지 않는다`() = runTest {
        start()
        territory.captureResult = CaptureResult.Queued
        emitAll(fix(a), fix(a))
        territory.captureResult = CaptureResult.AlreadyMine
        emitAll(fix(b), fix(b))
        assertEquals(1, tracking.state.value.capturedCount)
    }

    @Test
    fun `실패한 셀은 같은 셀의 다음 위치에서 다시 시도하고 한 번만 센다`() = runTest {
        start()
        territory.captureResult = CaptureResult.Failed(null)
        emitAll(fix(a), fix(a))
        territory.captureResult = CaptureResult.Captured
        emitAll(fix(a))
        assertEquals(listOf(grid.cellOf(a), grid.cellOf(a)), territory.captureCalls)
        assertEquals(1, tracking.state.value.capturedCount)
        emitAll(fix(a)) // 이제 lastCell — 더 부르지 않는다
        assertEquals(2, territory.captureCalls.size)
    }

    @Test
    fun `정확도가 나쁘면 캡처하지 않고 GPS 약함, 좋아지면 해제`() = runTest {
        start()
        emitAll(fix(a, accuracy = 80f))
        assertTrue(tracking.state.value.isGpsWeak)
        assertTrue(territory.captureCalls.isEmpty())
        emitAll(fix(a, accuracy = 10f))
        assertEquals(false, tracking.state.value.isGpsWeak)
    }

    @Test
    fun `위치가 끊겼다 돌아오면 후보와 속도 기준 fix 를 버린다 - 복구 뒤 한 번의 fix 로는 캡처하지 않는다`() = runTest {
        start()
        emitAll(fix(a), fix(a)) // a 캡처
        emitAll(fix(b), LocationUpdate.Unavailable, fix(b, time = 60_000L))
        assertEquals(listOf(grid.cellOf(a)), territory.captureCalls)
        emitAll(fix(b, time = 65_000L))
        assertEquals(listOf(grid.cellOf(a), grid.cellOf(b)), territory.captureCalls)
    }

    @Test
    fun `위치를 못 구하면(권한 회수·GPS 꺼짐) 죽지 않고 GPS 약함`() = runTest {
        start()
        locations.updates.emit(LocationUpdate.Unavailable)
        assertTrue(tracking.state.value.isGpsWeak)
        assertTrue(tracking.state.value.isTracking)
    }

    @Test
    fun `위치 스트림이 끝나면(권한 회수) 예외 없이 산책이 끝난다`() = runTest {
        locations.updatesOverride = flowOf(LocationUpdate.Unavailable)
        tracker.run()
        assertEquals(false, tracking.state.value.isTracking)
    }

    @Test
    fun `이미 큐에 있는 셀은 이번 산책 칸 수에 다시 세지 않는다`() = runTest {
        start()
        territory.captureResult = CaptureResult.Queued
        emitAll(fix(a), fix(a), fix(b), fix(b))
        territory.captureResult = CaptureResult.AlreadyQueued
        emitAll(fix(a), fix(a))
        assertEquals(2, tracking.state.value.capturedCount)
    }

    @Test
    fun `취소되면 추적 종료 상태로 돌아간다`() = runTest {
        val job = start()
        job.cancel()
        runCurrent()
        assertEquals(false, tracking.state.value.isTracking)
    }

    private val c = LatLngPoint(37.5697, 126.9780) // b 에서 북쪽 약 200 m

    @Test
    fun `판정을 통과한 fix 사이 거리를 더한다 - 첫 fix 는 기준만`() = runTest {
        start()
        emitAll(fix(a), fix(b), fix(c))
        assertEquals(2, tracking.distanceCalls.size)
        assertEquals(200.0, tracking.distanceCalls[0], 2.0)
        assertEquals(200.0, tracking.distanceCalls[1], 2.0)
        assertEquals(400.0, tracking.state.value.distanceMeters, 4.0)
    }

    @Test
    fun `같은 셀 반복(SameCell)과 후보(Unconfirmed)도 통과라 거리를 더한다`() = runTest {
        start()
        emitAll(fix(a), fix(a)) // 캡처 → 다음 a 는 SameCell
        emitAll(fix(a))
        assertEquals(2, tracking.distanceCalls.size) // a→a 0 m 두 번
        assertEquals(0.0, tracking.state.value.distanceMeters, 0.0)
    }

    @Test
    fun `비통과 fix 가 끼면 그 앞뒤 거리는 더하지 않는다`() = runTest {
        start()
        emitAll(fix(a), fix(b, accuracy = 80f), fix(c))
        // a→b(부정확) 안 더함, b→c 도 안 더함(기준이 c 로 새로 잡힘)
        assertTrue(tracking.distanceCalls.isEmpty())
        emitAll(fix(b))
        assertEquals(1, tracking.distanceCalls.size) // c→b 만
        assertEquals(200.0, tracking.distanceCalls[0], 2.0)
    }

    @Test
    fun `속도 초과·mock 도 기준을 끊는다`() = runTest {
        start()
        emitAll(fix(a), fix(b, speed = 30f), fix(c))
        assertTrue(tracking.distanceCalls.isEmpty())
        emitAll(
            fix(b),
            LocationUpdate.Fix(LocationSample(c, 10f, 1.2f, elapsedMillis = 0L, isMock = true)),
            fix(a),
        )
        assertEquals(1, tracking.distanceCalls.size) // c→b 만. mock 뒤 a 는 기준 리셋
    }

    @Test
    fun `Unavailable 뒤 첫 fix 는 거리를 더하지 않는다`() = runTest {
        start()
        emitAll(fix(a), LocationUpdate.Unavailable, fix(b))
        assertTrue(tracking.distanceCalls.isEmpty())
        emitAll(fix(c))
        assertEquals(1, tracking.distanceCalls.size)
    }
}
