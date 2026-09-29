package com.jaychoi.eattheland.core.data.location

import com.google.android.gms.location.Priority
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WalkLocationRequestTest {
    @Test
    fun `5초 고정밀도, 최소 이동 거리 없음 - 서 있어도 두 번째 fix 가 와야 2연속 캡처가 된다`() {
        val request = walkLocationRequest()
        assertEquals(Priority.PRIORITY_HIGH_ACCURACY, request.priority)
        assertEquals(5_000L, request.intervalMillis)
        assertEquals(0f, request.minUpdateDistanceMeters)
    }
}
