package com.jaychoi.eattheland.core.testing

import com.jaychoi.eattheland.core.data.TerritoryRepository
import com.jaychoi.eattheland.core.model.CaptureResult
import com.jaychoi.eattheland.core.model.Cell
import com.jaychoi.eattheland.core.model.CellId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeTerritoryRepository : TerritoryRepository {
    val cells = MutableStateFlow<List<Cell>>(emptyList())
    val requestedRegions = mutableListOf<Set<CellId>>()
    val captureCalls = mutableListOf<CellId>()
    var captureResult: CaptureResult = CaptureResult.Captured
    val pending = MutableStateFlow(0)
    var flushCalls = 0
        private set

    override fun observeCells(regions: Set<CellId>): Flow<List<Cell>> {
        requestedRegions += regions
        return cells
    }

    override suspend fun capture(cell: CellId): CaptureResult {
        captureCalls += cell
        return captureResult
    }

    override val pendingCount: Flow<Int> = pending

    override suspend fun flushPending(): Int {
        flushCalls++
        return pending.value
    }
}
