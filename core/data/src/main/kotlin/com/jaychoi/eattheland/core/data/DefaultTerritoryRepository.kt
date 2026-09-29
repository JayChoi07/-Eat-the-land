package com.jaychoi.eattheland.core.data

import com.jaychoi.eattheland.core.common.Clock
import com.jaychoi.eattheland.core.common.grid.HexGrid
import com.jaychoi.eattheland.core.data.sync.PendingCaptureQueue
import com.jaychoi.eattheland.core.model.CaptureResult
import com.jaychoi.eattheland.core.model.Cell
import com.jaychoi.eattheland.core.model.CellId
import com.jaychoi.eattheland.core.network.AuthDataSource
import com.jaychoi.eattheland.core.network.CaptureOutcome
import com.jaychoi.eattheland.core.network.CaptureRequest
import com.jaychoi.eattheland.core.network.CellDataSource
import com.jaychoi.eattheland.core.network.DataSourceException
import com.jaychoi.eattheland.core.network.toDomain
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class DefaultTerritoryRepository @Inject constructor(
    private val cells: CellDataSource,
    private val grid: HexGrid,
    private val auth: AuthDataSource,
    private val queue: PendingCaptureQueue,
    private val clock: Clock,
) : TerritoryRepository {
    // 리스너 오류는 지도 화면을 죽이지 않고 다시 구독한다.
    override fun observeCells(regions: Set<CellId>): Flow<List<Cell>> =
        cells.observe(regions.map { it.value }.toSet())
            .map { byId -> byId.mapNotNull { (id, dto) -> dto.toDomain(id)?.takeIf(::isOnGrid) } }
            .retryOnListenerError(fallback = emptyList())

    override val pendingCount: Flow<Int> = queue.count

    override suspend fun capture(cell: CellId): CaptureResult {
        val result = try {
            tryCapture(cell, walkedAtMillis = clock.nowMillis())
        } catch (e: CancellationException) {
            // 산책 종료(서비스 취소)로 끊긴 캡처 의도는 남긴다. 트랜잭션이 이미 커밋됐다면 재전송이 AlreadyMine 을 받는다.
            withContext(NonCancellable) { queue.enqueue(cell) }
            throw e
        }
        return if (result.isOffline()) {
            if (queue.enqueue(cell)) CaptureResult.Queued else CaptureResult.AlreadyQueued
        } else {
            result
        }
    }

    override suspend fun flushPending(): Int {
        val pending = queue.snapshot()
        var remaining = pending.size
        for (item in pending) {
            val result = tryCapture(item.cell, walkedAtMillis = item.queuedAtMillis)
            // 오프라인이면 여기서 멈춘다 — WorkManager 가 연결 뒤 다시 부른다.
            if (result.isOffline()) break
            if (result.isSettled()) {
                queue.remove(item)
                remaining--
            }
        }
        return remaining
    }

    // 성공·이미 내 셀·규칙 거부(영구 실패)는 큐에서 끝난 것. 일시 오류(경합·Unknown)는 남겨 다음 재시도에 맡긴다.
    private fun CaptureResult.isSettled(): Boolean =
        this !is CaptureResult.Failed || kindOrNull() == DataSourceException.Kind.PermissionDenied

    // R-23-05: 데이터소스 예외를 여기서 도메인 결과로 바꾼다. 색은 프로필과 같은 규칙(colorFor).
    // Firestore 트랜잭션은 오프라인에서 실패하지 않고 연결을 기다린다(실기기 확인) — 시간이 지나면 오프라인으로 본다.
    // 취소된 트랜잭션이 나중에 커밋돼도 재전송은 AlreadyMine 이라 두 번 세지 않는다.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun tryCapture(cell: CellId, walkedAtMillis: Long): CaptureResult {
        val uid = auth.uid.first() ?: return CaptureResult.Failed(null)
        val request = CaptureRequest(
            cellId = cell.value,
            region = grid.regionOf(cell).value,
            uid = uid,
            color = colorFor(uid),
            walkedAtMillis = walkedAtMillis,
        )
        return try {
            withTimeout(CAPTURE_TIMEOUT_MS) {
                when (cells.capture(request)) {
                    CaptureOutcome.Captured -> CaptureResult.Captured
                    CaptureOutcome.AlreadyMine -> CaptureResult.AlreadyMine
                }
            }
        } catch (e: TimeoutCancellationException) {
            CaptureResult.Failed(DataSourceException(DataSourceException.Kind.Offline, e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CaptureResult.Failed(e)
        }
    }

    private fun CaptureResult.isOffline(): Boolean =
        kindOrNull() == DataSourceException.Kind.Offline

    private fun CaptureResult.kindOrNull(): DataSourceException.Kind? =
        ((this as? CaptureResult.Failed)?.cause as? DataSourceException)?.kind

    // 문서는 누구나 쓸 수 있으므로(스펙 §4) 격자에 없는 ID·거짓 region 은 버린다.
    // 검사 없이 넘기면 HexGrid 가 예외를 던져 같은 지역을 보는 모든 사용자의 앱이 죽는다.
    private fun isOnGrid(cell: Cell): Boolean =
        grid.isValidCell(cell.id) && grid.regionOf(cell.id) == cell.region

    private companion object {
        const val CAPTURE_TIMEOUT_MS = 10_000L
    }
}
