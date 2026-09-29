package com.jaychoi.eattheland.core.data

import com.jaychoi.eattheland.core.model.CaptureResult
import com.jaychoi.eattheland.core.model.Cell
import com.jaychoi.eattheland.core.model.CellId
import kotlinx.coroutines.flow.Flow

interface TerritoryRepository {
    fun observeCells(regions: Set<CellId>): Flow<List<Cell>>

    /** 스펙 §4 capture. 오프라인이면 큐에 넣고 Queued. */
    suspend fun capture(cell: CellId): CaptureResult

    /** 큐에 남은 셀 수. */
    val pendingCount: Flow<Int>

    /** 큐를 오래된 순으로 재전송한다. 오프라인이면 멈춘다. 돌려주는 값은 남은 개수. */
    suspend fun flushPending(): Int
}
