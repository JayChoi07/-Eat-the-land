package com.jaychoi.eattheland.core.common.grid

import com.jaychoi.eattheland.core.model.CellId
import com.jaychoi.eattheland.core.model.LatLngPoint
import com.uber.h3core.H3Core
import javax.inject.Inject
import javax.inject.Singleton

/**
 * H3 4.5.0 (h3-android AAR). 네이티브 로드는 한 번이면 되므로 앱 전역 인스턴스 하나 (R-14-06).
 * AAR 에는 armeabi-v7a·arm64-v8a 만 들어 있어 x86_64 에뮬레이터에서는 첫 호출 시 UnsatisfiedLinkError 가 난다 —
 * 생성 시점이 아니라 첫 사용 시점(지도 화면)까지 미뤄 온보딩은 에뮬레이터에서도 돌게 한다. 검증은 실기기에서 한다.
 */
@Singleton
class H3HexGrid @Inject constructor() : HexGrid {
    private val h3: H3Core by lazy { H3Core.newInstance() }

    override fun cellOf(point: LatLngPoint, res: Int): CellId =
        CellId(h3.latLngToCellAddress(point.lat, point.lng, res))

    override fun regionOf(cell: CellId): CellId =
        CellId(h3.cellToParentAddress(cell.value, HexGrid.REGION_RES))

    override fun boundary(cell: CellId): List<LatLngPoint> =
        h3.cellToBoundary(cell.value).map { LatLngPoint(it.lat, it.lng) }

    override fun regionsAround(center: LatLngPoint): Set<CellId> {
        val region = h3.latLngToCellAddress(center.lat, center.lng, HexGrid.REGION_RES)
        return h3.gridDisk(region, 1).map(::CellId).toSet()
    }
}
