package com.jaychoi.eattheland.core.domain

import com.jaychoi.eattheland.core.model.CaptureDecision
import com.jaychoi.eattheland.core.model.CellId
import com.jaychoi.eattheland.core.model.LatLngPoint
import com.jaychoi.eattheland.core.model.LocationSample
import com.jaychoi.eattheland.core.model.SkipReason
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureCellUseCaseTest {
    private val useCase = CaptureCellUseCase()
    private val here = CellId("8b30e1d8c0b1fff")
    private val there = CellId("8b30e1d8c0a6fff")

    private fun sample(
        accuracy: Float = 10f,
        speed: Float? = 1.2f,
        mock: Boolean = false,
    ) = LocationSample(LatLngPoint(37.5665, 126.9780), accuracy, speed, timeMillis = 1_000L, isMock = mock)

    @Test
    fun `정확도·속도가 좋고 새 셀이면 Capture`() {
        assertEquals(CaptureDecision.Capture, useCase(sample(), here, lastCell = there))
        assertEquals(CaptureDecision.Capture, useCase(sample(), here, lastCell = null))
    }

    @Test
    fun `mock 위치는 MockLocation`() {
        assertEquals(CaptureDecision.Skip(SkipReason.MockLocation), useCase(sample(mock = true), here, null))
    }

    @Test
    fun `정확도 50m 초과는 Inaccurate, 50m 는 통과`() {
        assertEquals(CaptureDecision.Skip(SkipReason.Inaccurate), useCase(sample(accuracy = 50.1f), here, null))
        assertEquals(CaptureDecision.Capture, useCase(sample(accuracy = 50f), here, null))
    }

    @Test
    fun `20 km h 초과는 TooFast, 속도 미상은 통과`() {
        assertEquals(CaptureDecision.Skip(SkipReason.TooFast), useCase(sample(speed = 5.6f), here, null))
        assertEquals(CaptureDecision.Capture, useCase(sample(speed = 5.5f), here, null))
        assertEquals(CaptureDecision.Capture, useCase(sample(speed = null), here, null))
    }

    @Test
    fun `직전과 같은 셀이면 SameCell`() {
        assertEquals(CaptureDecision.Skip(SkipReason.SameCell), useCase(sample(), here, lastCell = here))
    }

    @Test
    fun `여러 조건이 겹치면 mock, 정확도, 속도, 같은 셀 순으로 본다`() {
        assertEquals(
            CaptureDecision.Skip(SkipReason.Inaccurate),
            useCase(sample(accuracy = 80f, speed = 9f), here, lastCell = here),
        )
    }
}
