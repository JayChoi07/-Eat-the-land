package com.jaychoi.eattheland.core.data

import com.jaychoi.eattheland.core.common.grid.HexGrid
import com.jaychoi.eattheland.core.model.Cell
import com.jaychoi.eattheland.core.model.CellId
import com.jaychoi.eattheland.core.network.CellDataSource
import com.jaychoi.eattheland.core.network.toDomain
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DefaultTerritoryRepository @Inject constructor(
    private val cells: CellDataSource,
    private val grid: HexGrid,
) : TerritoryRepository {
    // 리스너 오류는 지도 화면을 죽이지 않고 다시 구독한다.
    override fun observeCells(regions: Set<CellId>): Flow<List<Cell>> =
        cells.observe(regions.map { it.value }.toSet())
            .map { byId -> byId.mapNotNull { (id, dto) -> dto.toDomain(id)?.takeIf(::isOnGrid) } }
            .retryOnListenerError(fallback = emptyList())

    // 문서는 누구나 쓸 수 있으므로(스펙 §4) 격자에 없는 ID·거짓 region 은 버린다.
    // 검사 없이 넘기면 HexGrid 가 예외를 던져 같은 지역을 보는 모든 사용자의 앱이 죽는다.
    private fun isOnGrid(cell: Cell): Boolean =
        grid.isValidCell(cell.id) && grid.regionOf(cell.id) == cell.region
}
