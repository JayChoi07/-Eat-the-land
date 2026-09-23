package com.jaychoi.eattheland.core.testing

import com.jaychoi.eattheland.core.data.TerritoryRepository
import com.jaychoi.eattheland.core.model.Cell
import com.jaychoi.eattheland.core.model.CellId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeTerritoryRepository : TerritoryRepository {
    val cells = MutableStateFlow<List<Cell>>(emptyList())
    val requestedRegions = mutableListOf<Set<CellId>>()

    override fun observeCells(regions: Set<CellId>): Flow<List<Cell>> {
        requestedRegions += regions
        return cells
    }
}
