package com.jaychoi.eattheland.core.data.sync

import com.jaychoi.eattheland.core.common.Clock
import com.jaychoi.eattheland.core.datastore.PendingCapture
import com.jaychoi.eattheland.core.datastore.PendingCaptureDataSource
import com.jaychoi.eattheland.core.model.CellId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 큐 항목. queuedAtMillis 는 "어느 버전을 보냈는가"의 식별자 — 보낸 뒤 더 새로 들어온 같은 셀은 지우지 않는다. */
data class PendingCell(val cell: CellId, val queuedAtMillis: Long)

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

    /** 돌려주는 값은 "새로 들어간 셀인가"(이미 있으면 시각만 갱신). */
    suspend fun enqueue(cell: CellId): Boolean {
        val now = clock.nowMillis()
        var added = false
        source.update { list ->
            added = list.none { it.cellId == cell.value }
            (list.filterNot { it.cellId == cell.value } + PendingCapture(cell.value, now))
                .sortedBy { it.queuedAtMillis }
                .takeLast(MAX_ITEMS)
        }
        scheduler.scheduleFlush()
        return added
    }

    /** 만료 항목을 지운 뒤 남은 셀을 오래된 순으로. */
    suspend fun snapshot(): List<PendingCell> {
        val cutoff = clock.nowMillis() - MAX_AGE_MILLIS
        var fresh: List<PendingCapture> = emptyList()
        source.update { list ->
            fresh = list.filter { it.queuedAtMillis >= cutoff }.sortedBy { it.queuedAtMillis }
            fresh
        }
        return fresh.map { PendingCell(CellId(it.cellId), it.queuedAtMillis) }
    }

    /** 보낸 항목만 지운다 — 그 사이 더 새 시각으로 다시 들어온 같은 셀은 남긴다. */
    suspend fun remove(sent: PendingCell) {
        source.update { list ->
            list.filterNot {
                it.cellId == sent.cell.value && it.queuedAtMillis <= sent.queuedAtMillis
            }
        }
    }

    /** 계정 삭제 뒤 — 옛 계정의 미전송 캡처가 새 계정으로 흘러가지 않게 전부 버린다. */
    suspend fun clear() {
        source.update { emptyList() }
    }

    private companion object {
        const val MAX_ITEMS = 300
        const val MAX_AGE_MILLIS = 24L * 60 * 60 * 1_000
    }
}
