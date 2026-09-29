package com.jaychoi.eattheland.feature.map

import com.jaychoi.eattheland.feature.map.ui.RelativeTime
import com.jaychoi.eattheland.feature.map.ui.relativeTime
import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeTest {
    private val now = 1_800_000_000_000L
    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour

    @Test
    fun `1분 미만 방금, 분·시간·어제·일, 7일부터 날짜`() {
        assertEquals(RelativeTime.JustNow, relativeTime(now, now))
        assertEquals(RelativeTime.JustNow, relativeTime(now, now - 59_999L))
        assertEquals(RelativeTime.Minutes(1), relativeTime(now, now - minute))
        assertEquals(RelativeTime.Minutes(59), relativeTime(now, now - 59 * minute - 30_000L))
        assertEquals(RelativeTime.Hours(1), relativeTime(now, now - hour))
        assertEquals(RelativeTime.Hours(23), relativeTime(now, now - 23 * hour - 59 * minute))
        assertEquals(RelativeTime.Yesterday, relativeTime(now, now - day))
        assertEquals(RelativeTime.Yesterday, relativeTime(now, now - 2 * day + 1))
        assertEquals(RelativeTime.Days(2), relativeTime(now, now - 2 * day))
        assertEquals(RelativeTime.Days(6), relativeTime(now, now - 6 * day - hour))
        assertEquals(RelativeTime.Date(now - 7 * day), relativeTime(now, now - 7 * day))
    }

    @Test
    fun `미래 시각(기기 시계 오차)은 방금`() {
        assertEquals(RelativeTime.JustNow, relativeTime(now, now + 5 * minute))
    }
}
