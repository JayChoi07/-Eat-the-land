package com.jaychoi.eattheland.core.domain

import com.jaychoi.eattheland.core.model.CaptureDecision
import com.jaychoi.eattheland.core.model.CellId
import com.jaychoi.eattheland.core.model.LatLngPoint
import com.jaychoi.eattheland.core.model.LocationSample
import com.jaychoi.eattheland.core.model.SkipReason
import com.jaychoi.eattheland.core.model.WalkContext
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureCellUseCaseTest {
    private val useCase = CaptureCellUseCase()
    private val here = CellId("8b30e1d8c0b1fff")
    private val there = CellId("8b30e1d8c0a6fff")
    private val origin = LatLngPoint(37.5665, 126.9780)

    // 위도 0.00054° ≈ 60 m, 0.00018° ≈ 20 m (GeoTest 기준)
    private val sixtyMetersNorth = LatLngPoint(37.56704, 126.9780)
    private val twentyMetersNorth = LatLngPoint(37.56668, 126.9780)

    private fun sample(
        point: LatLngPoint = origin,
        accuracy: Float = 10f,
        speed: Float? = 1.2f,
        time: Long = 1_000L,
        mock: Boolean = false,
    ) = LocationSample(point, accuracy, speed, timeMillis = time, isMock = mock)

    private val fresh = WalkContext()
    private val candidateHere = WalkContext(candidateCell = here)

    @Test
    fun `새 셀의 첫 fix 는 Unconfirmed, 같은 셀 두 번째 fix 는 Capture`() {
        assertEquals(CaptureDecision.Skip(SkipReason.Unconfirmed), useCase(sample(), here, fresh))
        assertEquals(CaptureDecision.Capture, useCase(sample(), here, candidateHere))
    }

    @Test
    fun `후보와 다른 셀이면 다시 Unconfirmed`() {
        assertEquals(CaptureDecision.Skip(SkipReason.Unconfirmed), useCase(sample(), there, candidateHere))
    }

    @Test
    fun `직전과 같은 셀이면 후보와 같아도 SameCell`() {
        val context = WalkContext(lastCell = here, candidateCell = here)
        assertEquals(CaptureDecision.Skip(SkipReason.SameCell), useCase(sample(), here, context))
    }

    @Test
    fun `mock 위치는 MockLocation`() {
        assertEquals(
            CaptureDecision.Skip(SkipReason.MockLocation),
            useCase(sample(mock = true), here, candidateHere),
        )
    }

    @Test
    fun `정확도 50m 초과는 Inaccurate, 50m 는 통과`() {
        assertEquals(
            CaptureDecision.Skip(SkipReason.Inaccurate),
            useCase(sample(accuracy = 50.1f), here, candidateHere),
        )
        assertEquals(CaptureDecision.Capture, useCase(sample(accuracy = 50f), here, candidateHere))
    }

    @Test
    fun `제공자 속도가 20 km h 를 넘으면 TooFast`() {
        assertEquals(CaptureDecision.Skip(SkipReason.TooFast), useCase(sample(speed = 5.6f), here, candidateHere))
        assertEquals(CaptureDecision.Capture, useCase(sample(speed = 5.5f), here, candidateHere))
    }

    @Test
    fun `속도 미상이면 직전 fix 와의 거리로 계산한다 - 5초에 60m 는 TooFast, 20m 는 통과`() {
        val last = sample(point = origin, speed = null, time = 0L)
        val context = candidateHere.copy(lastSample = last)
        assertEquals(
            CaptureDecision.Skip(SkipReason.TooFast),
            useCase(sample(point = sixtyMetersNorth, speed = null, time = 5_000L), here, context),
        )
        assertEquals(
            CaptureDecision.Capture,
            useCase(sample(point = twentyMetersNorth, speed = null, time = 5_000L), here, context),
        )
    }

    @Test
    fun `속도 미상이고 직전 fix 가 없거나 시간이 흐르지 않았으면 통과`() {
        assertEquals(CaptureDecision.Capture, useCase(sample(speed = null), here, candidateHere))
        val last = sample(point = origin, speed = null, time = 5_000L)
        val context = candidateHere.copy(lastSample = last)
        assertEquals(
            CaptureDecision.Capture,
            useCase(sample(point = sixtyMetersNorth, speed = null, time = 5_000L), here, context),
        )
    }

    @Test
    fun `제공자 속도가 있으면 거리 계산보다 우선한다`() {
        val last = sample(point = origin, speed = null, time = 0L)
        val context = candidateHere.copy(lastSample = last)
        // 60 m/5 s 지만 제공자는 1.2 m/s 라고 한다 → 통과
        assertEquals(
            CaptureDecision.Capture,
            useCase(sample(point = sixtyMetersNorth, speed = 1.2f, time = 5_000L), here, context),
        )
    }

    @Test
    fun `여러 조건이 겹치면 mock, 정확도, 속도, 같은 셀, 후보 순으로 본다`() {
        val context = WalkContext(lastCell = here, candidateCell = here)
        assertEquals(
            CaptureDecision.Skip(SkipReason.Inaccurate),
            useCase(sample(accuracy = 80f, speed = 9f), here, context),
        )
        assertEquals(
            CaptureDecision.Skip(SkipReason.TooFast),
            useCase(sample(speed = 9f), here, context),
        )
    }
}
