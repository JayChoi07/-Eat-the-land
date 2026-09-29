package com.jaychoi.eattheland.core.common.grid

import com.jaychoi.eattheland.core.model.CellId
import com.jaychoi.eattheland.core.model.LatLngPoint
import com.uber.h3core.H3Core
import javax.inject.Inject
import javax.inject.Singleton

/**
 * H3 4.5.0 (h3-android AAR). 네이티브 로드는 한 번이면 되므로 앱 전역 인스턴스 하나 (R-14-06).
 * Android 에서는 `newSystemInstance()` 를 쓴다 — AAR 의 jniLibs(libh3-java.so)를 System.loadLibrary 로 읽는다.
 * `newInstance()` 는 JAR 리소스에서 네이티브를 꺼내는 데스크톱 방식이라 Android 에서 UnsatisfiedLinkError 가 난다.
 * AAR 은 ARM 전용이므로 :app 이 abiFilters 로 ARM 만 담는다. 로드는 첫 사용 시점까지 미룬다.
 */
@Singleton
class H3HexGrid @Inject constructor() : HexGrid {
    private val h3: H3Core by lazy { H3Core.newSystemInstance() }

    override fun cellOf(point: LatLngPoint, res: Int): CellId =
        CellId(h3.latLngToCellAddress(point.lat, point.lng, res))

    override fun regionOf(cell: CellId): CellId =
        CellId(h3.cellToParentAddress(cell.value, HexGrid.REGION_RES))

    override fun boundary(cell: CellId): List<LatLngPoint> =
        h3.cellToBoundary(cell.value).map { LatLngPoint(it.lat, it.lng) }

    // H3 는 16진수가 아닌 문자열에 NumberFormatException 을 던진다 — 조작된 문서 ID 는 "유효하지 않음"이다.
    override fun isValidCell(cell: CellId): Boolean = runCatching {
        h3.isValidCell(cell.value) && h3.getResolution(cell.value) == HexGrid.CELL_RES
    }.getOrDefault(false)

    override fun regionsAround(center: LatLngPoint): Set<CellId> {
        val region = h3.latLngToCellAddress(center.lat, center.lng, HexGrid.REGION_RES)
        return h3.gridDisk(region, 1).map(::CellId).toSet()
    }
}
