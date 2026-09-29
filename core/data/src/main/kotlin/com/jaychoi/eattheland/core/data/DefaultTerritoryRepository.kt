package com.jaychoi.eattheland.core.data

import com.jaychoi.eattheland.core.common.grid.HexGrid
import com.jaychoi.eattheland.core.data.sync.PendingCaptureQueue
import com.jaychoi.eattheland.core.model.CaptureResult
import com.jaychoi.eattheland.core.model.Cell
import com.jaychoi.eattheland.core.model.CellId
import com.jaychoi.eattheland.core.network.AuthDataSource
import com.jaychoi.eattheland.core.network.CaptureOutcome
import com.jaychoi.eattheland.core.network.CellDataSource
import com.jaychoi.eattheland.core.network.DataSourceException
import com.jaychoi.eattheland.core.network.toDomain
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class DefaultTerritoryRepository @Inject constructor(
    private val cells: CellDataSource,
    private val grid: HexGrid,
    private val auth: AuthDataSource,
    private val queue: PendingCaptureQueue,
) : TerritoryRepository {
    // 리스너 오류는 지도 화면을 죽이지 않고 다시 구독한다.
    override fun observeCells(regions: Set<CellId>): Flow<List<Cell>> =
        cells.observe(regions.map { it.value }.toSet())
            .map { byId -> byId.mapNotNull { (id, dto) -> dto.toDomain(id)?.takeIf(::isOnGrid) } }
            .retryOnListenerError(fallback = emptyList())

    override val pendingCount: Flow<Int> = queue.count

    override suspend fun capture(cell: CellId): CaptureResult {
        val result = tryCapture(cell)
        return if (result.isOffline()) {
            queue.enqueue(cell)
            CaptureResult.Queued
        } else {
            result
        }
    }

    override suspend fun flushPending(): Int {
        val pending = queue.snapshot()
        var sent = 0
        for (cell in pending) {
            // 오프라인이면 여기서 멈춘다 — WorkManager 가 연결 뒤 다시 부른다. 그 외 실패는 항목을 버린다(독약 방지).
            if (tryCapture(cell).isOffline()) break
            queue.remove(cell)
            sent++
        }
        return pending.size - sent
    }

    // R-23-05: 데이터소스 예외를 여기서 도메인 결과로 바꾼다. 색은 프로필과 같은 규칙(colorFor).
    @Suppress("TooGenericExceptionCaught")
    private suspend fun tryCapture(cell: CellId): CaptureResult {
        val uid = auth.uid.first() ?: return CaptureResult.Failed(null)
        return try {
            when (cells.capture(cell.value, grid.regionOf(cell).value, uid, colorFor(uid))) {
                CaptureOutcome.Captured -> CaptureResult.Captured
                CaptureOutcome.AlreadyMine -> CaptureResult.AlreadyMine
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CaptureResult.Failed(e)
        }
    }

    private fun CaptureResult.isOffline(): Boolean {
        val cause = (this as? CaptureResult.Failed)?.cause
        return cause is DataSourceException && cause.kind == DataSourceException.Kind.Offline
    }

    // 문서는 누구나 쓸 수 있으므로(스펙 §4) 격자에 없는 ID·거짓 region 은 버린다.
    // 검사 없이 넘기면 HexGrid 가 예외를 던져 같은 지역을 보는 모든 사용자의 앱이 죽는다.
    private fun isOnGrid(cell: Cell): Boolean =
        grid.isValidCell(cell.id) && grid.regionOf(cell.id) == cell.region
}
