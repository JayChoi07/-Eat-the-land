package com.jaychoi.eattheland.feature.map

import com.jaychoi.eattheland.feature.map.ui.DistanceText
import com.jaychoi.eattheland.feature.map.ui.DurationText
import com.jaychoi.eattheland.feature.map.ui.formatDistance
import com.jaychoi.eattheland.feature.map.ui.formatDuration
import org.junit.Assert.assertEquals
import org.junit.Test

class WalkFormatTest {
    @Test
    fun `1 km 미만은 m 정수, 이상은 km 소수 1자리`() {
        assertEquals(DistanceText("0", isKm = false), formatDistance(0.0))
        assertEquals(DistanceText("850", isKm = false), formatDistance(850.4))
        assertEquals(DistanceText("999", isKm = false), formatDistance(999.4))
        assertEquals(DistanceText("1.0", isKm = true), formatDistance(999.5))
        assertEquals(DistanceText("1.0", isKm = true), formatDistance(1_000.0))
        assertEquals(DistanceText("1.8", isKm = true), formatDistance(1_830.0))
        assertEquals(DistanceText("12.3", isKm = true), formatDistance(12_345.0))
    }

    @Test
    fun `시간은 분 단위, 60분부터 시간·분`() {
        assertEquals(DurationText(hours = 0, minutes = 0), formatDuration(0L))
        assertEquals(DurationText(hours = 0, minutes = 0), formatDuration(59_999L))
        assertEquals(DurationText(hours = 0, minutes = 24), formatDuration(24 * 60_000L + 30_000L))
        assertEquals(DurationText(hours = 1, minutes = 0), formatDuration(60 * 60_000L))
        assertEquals(DurationText(hours = 1, minutes = 3), formatDuration(63 * 60_000L))
        assertEquals(DurationText(hours = 2, minutes = 59), formatDuration(179 * 60_000L))
    }
}
