package com.jaychoi.eattheland.core.data.sync

import com.jaychoi.eattheland.core.common.Clock
import com.jaychoi.eattheland.core.datastore.PendingCapture
import com.jaychoi.eattheland.core.datastore.PendingCaptureDataSource
import com.jaychoi.eattheland.core.model.CellId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 오프라인 캡처 큐 정책(사용자 결정 2026-09-29): 최대 300칸(초과 시 가장 오래된 것 폐기), 24시간 지나면 폐기,
 * 같은 셀은 하나만(최근 시각). 저장은 :core:datastore, 재전송 예약은 [PendingCaptureScheduler].
 */
class PendingCaptureQueue @Inject constructor(
    private val source: PendingCaptureDataSource,
    private val scheduler: PendingCaptureScheduler,
    private val clock: Clock,
) {
    val count: Flow<Int> = source.pending.map { it.size }

    suspend fun enqueue(cell: CellId) {
        val now = clock.nowMillis()
        source.update { list ->
            (list.filterNot { it.cellId == cell.value } + PendingCapture(cell.value, now))
                .sortedBy { it.queuedAtMillis }
                .takeLast(MAX_ITEMS)
        }
        scheduler.scheduleFlush()
    }

    /** 만료 항목을 지운 뒤 남은 셀을 오래된 순으로. */
    suspend fun snapshot(): List<CellId> {
        val cutoff = clock.nowMillis() - MAX_AGE_MILLIS
        var fresh: List<PendingCapture> = emptyList()
        source.update { list ->
            fresh = list.filter { it.queuedAtMillis >= cutoff }.sortedBy { it.queuedAtMillis }
            fresh
        }
        return fresh.map { CellId(it.cellId) }
    }

    suspend fun remove(cell: CellId) {
        source.update { list -> list.filterNot { it.cellId == cell.value } }
    }

    private companion object {
        const val MAX_ITEMS = 300
        const val MAX_AGE_MILLIS = 24L * 60 * 60 * 1_000
    }
}
