package com.jaychoi.eattheland.core.data.location

import android.location.Location
import com.jaychoi.eattheland.core.model.LatLngPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocationMappingTest {
    private fun location(speed: Float? = 1.5f, mock: Boolean = false) = Location("fused").apply {
        latitude = 37.5665
        longitude = 126.9780
        accuracy = 12f
        time = 1_000L
        elapsedRealtimeNanos = 7_000_000_000L
        if (speed != null) this.speed = speed
        isMock = mock
    }

    @Test
    fun `위도·경도·정확도·속도·mock 을 옮기고, 시각은 벽시계가 아니라 부팅 후 경과 시간(ms)`() {
        val sample = location(mock = true).toSample()
        assertEquals(LatLngPoint(37.5665, 126.9780), sample.point)
        assertEquals(12f, sample.accuracyMeters)
        assertEquals(1.5f, sample.speedMps)
        assertEquals(7_000L, sample.elapsedMillis)
        assertEquals(true, sample.isMock)
    }

    @Test
    fun `속도가 없으면 null`() {
        assertNull(location(speed = null).toSample().speedMps)
    }
}
