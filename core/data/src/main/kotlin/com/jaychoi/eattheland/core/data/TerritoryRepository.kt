package com.jaychoi.eattheland.core.data

import com.jaychoi.eattheland.core.model.Cell
import com.jaychoi.eattheland.core.model.CellId
import kotlinx.coroutines.flow.Flow

interface TerritoryRepository {
    fun observeCells(regions: Set<CellId>): Flow<List<Cell>>
}
