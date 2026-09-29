package com.jaychoi.eattheland

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoubleBackGateTest {
    private val gate = DoubleBackGate(windowMillis = 2_000L)

    @Test
    fun `첫 번째는 종료가 아니고, 2초 안의 두 번째는 종료`() {
        assertFalse(gate.press(nowMillis = 10_000L))
        assertTrue(gate.press(nowMillis = 11_999L))
    }

    @Test
    fun `2초가 지나면 다시 첫 번째로 센다`() {
        assertFalse(gate.press(nowMillis = 10_000L))
        assertFalse(gate.press(nowMillis = 12_000L))
        assertTrue(gate.press(nowMillis = 13_000L))
    }
}
