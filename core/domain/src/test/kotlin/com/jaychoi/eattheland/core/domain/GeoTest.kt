package com.jaychoi.eattheland.core.domain

import com.jaychoi.eattheland.core.model.LatLngPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class GeoTest {
    private val cityHall = LatLngPoint(37.5665, 126.9780)

    @Test
    fun `같은 점은 0 m`() {
        assertEquals(0.0, distanceMeters(cityHall, cityHall), 0.0)
    }

    @Test
    fun `위도 0_001도는 약 111 m`() {
        assertEquals(111.2, distanceMeters(cityHall, LatLngPoint(37.5675, 126.9780)), 0.5)
    }

    @Test
    fun `서울 위도에서 경도 0_001도는 약 88 m`() {
        assertEquals(88.2, distanceMeters(cityHall, LatLngPoint(37.5665, 126.9790)), 0.5)
    }
}
