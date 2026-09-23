package com.jaychoi.eattheland.core.common.grid

import com.jaychoi.eattheland.core.model.LatLngPoint
import com.jaychoi.eattheland.core.testing.FakeHexGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FakeHexGridTest {
    private val grid = FakeHexGrid()

    @Test
    fun `같은 소수점 3자리 좌표는 같은 셀`() {
        val a = grid.cellOf(LatLngPoint(37.5661, 126.9780))
        val b = grid.cellOf(LatLngPoint(37.56612, 126.97801))
        assertEquals(a, b)
    }

    @Test
    fun `60m 떨어진 좌표는 다른 셀이지만 같은 region`() {
        val a = grid.cellOf(LatLngPoint(37.5661, 126.9780))
        val b = grid.cellOf(LatLngPoint(37.5679, 126.9780))
        assertNotEquals(a, b)
        assertEquals(grid.regionOf(a), grid.regionOf(b))
    }
}
