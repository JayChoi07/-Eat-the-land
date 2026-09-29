package com.jaychoi.eattheland.core.common.grid

import com.jaychoi.eattheland.core.model.CellId
import com.jaychoi.eattheland.core.model.LatLngPoint

/**
 * 육각 격자 계산 경계. 구현은 H3(네이티브)이라 JVM 단위 테스트에서는 FakeHexGrid(:core:testing)로 갈아끼운다.
 * 스펙 §2: 셀 해상도 11(폭 ≈ 50 m), 뷰포트 조회 키는 해상도 8(한 칸 ≈ 0.74 km², 셀 343개 — v3).
 */
interface HexGrid {
    fun cellOf(point: LatLngPoint, res: Int = CELL_RES): CellId

    fun regionOf(cell: CellId): CellId

    fun boundary(cell: CellId): List<LatLngPoint>

    /** 서버에서 온 문서 ID 가 실제 셀([CELL_RES])인가. 아니면 [regionOf]·[boundary] 에 넘기면 안 된다. */
    fun isValidCell(cell: CellId): Boolean

    /** 중심점이 속한 region 과 그 이웃 6개. 뷰포트 리스너 키로 쓴다(Firestore `in` 한도 30 미만). */
    fun regionsAround(center: LatLngPoint): Set<CellId>

    companion object {
        const val CELL_RES = 11
        const val REGION_RES = 8
    }
}
