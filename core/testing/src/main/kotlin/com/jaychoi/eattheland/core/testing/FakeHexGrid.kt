package com.jaychoi.eattheland.core.testing

import com.jaychoi.eattheland.core.common.grid.HexGrid
import com.jaychoi.eattheland.core.model.CellId
import com.jaychoi.eattheland.core.model.LatLngPoint
import java.util.Locale

/**
 * 결정적 격자. 셀 = 위경도를 소수점 3자리로 자른 문자열, region = 2자리로 자른 문자열.
 * 육각형 기하는 흉내 내지 않는다 — 소비 코드가 "같은 셀인가·어느 region인가"만 물어보기 때문이다.
 */
class FakeHexGrid : HexGrid {
    override fun cellOf(point: LatLngPoint, res: Int): CellId = CellId(key(point, digits = 3))

    override fun regionOf(cell: CellId): CellId {
        val (lat, lng) = cell.value.split("_").map { it.toDouble() }
        return CellId(key(LatLngPoint(lat, lng), digits = 2))
    }

    override fun boundary(cell: CellId): List<LatLngPoint> {
        val (lat, lng) = cell.value.split("_").map { it.toDouble() }
        val d = 0.0005
        return listOf(
            LatLngPoint(lat - d, lng - d),
            LatLngPoint(lat - d, lng + d),
            LatLngPoint(lat + d, lng + d),
            LatLngPoint(lat + d, lng - d),
        )
    }

    override fun isValidCell(cell: CellId): Boolean = CELL_PATTERN.matches(cell.value)

    override fun regionsAround(center: LatLngPoint): Set<CellId> = setOf(regionOf(cellOf(center)))

    private fun key(p: LatLngPoint, digits: Int): String =
        String.format(Locale.US, "%.${digits}f_%.${digits}f", p.lat, p.lng)

    private companion object {
        val CELL_PATTERN = Regex("""^-?\d+\.\d{3}_-?\d+\.\d{3}$""")
    }
}
