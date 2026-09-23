package com.jaychoi.eattheland.core.data

import com.jaychoi.eattheland.core.model.Cell
import com.jaychoi.eattheland.core.model.CellId
import com.jaychoi.eattheland.core.network.CellDataSource
import com.jaychoi.eattheland.core.network.DataSourceException
import com.jaychoi.eattheland.core.network.toDomain
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class DefaultTerritoryRepository @Inject constructor(
    private val cells: CellDataSource,
) : TerritoryRepository {
    // 리스너 오류는 빈 목록으로 흘려 지도 화면이 죽지 않게 한다. 뷰포트가 바뀌면 다시 구독된다.
    override fun observeCells(regions: Set<CellId>): Flow<List<Cell>> =
        cells.observe(regions.map { it.value }.toSet())
            .map { byId -> byId.mapNotNull { (id, dto) -> dto.toDomain(id) } }
            .catch { if (it is DataSourceException) emit(emptyList()) else throw it }
}
