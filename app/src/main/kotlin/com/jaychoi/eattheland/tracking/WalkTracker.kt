package com.jaychoi.eattheland.tracking

import com.jaychoi.eattheland.core.common.grid.HexGrid
import com.jaychoi.eattheland.core.data.TerritoryRepository
import com.jaychoi.eattheland.core.data.location.LocationRepository
import com.jaychoi.eattheland.core.data.tracking.TrackingRepository
import com.jaychoi.eattheland.core.domain.CaptureCellUseCase
import com.jaychoi.eattheland.core.model.CaptureDecision
import com.jaychoi.eattheland.core.model.CaptureResult
import com.jaychoi.eattheland.core.model.CellId
import com.jaychoi.eattheland.core.model.LocationSample
import com.jaychoi.eattheland.core.model.LocationUpdate
import com.jaychoi.eattheland.core.model.SkipReason
import com.jaychoi.eattheland.core.model.WalkContext
import javax.inject.Inject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 산책 한 번: 위치 → 판정(UseCase) → 캡처(Repository) → 상태. 서비스가 lifecycleScope 에서 [run] 을 돌리고
 * 서비스가 죽으면 취소된다. 캡처는 순차(한 셀씩)라 큐 순서가 걸은 순서와 같다.
 * 판정에 필요한 직전 상태([WalkContext])는 fix 마다 여기서 갱신한다(스펙 §3 v3).
 */
class WalkTracker @Inject constructor(
    private val locations: LocationRepository,
    private val territory: TerritoryRepository,
    private val tracking: TrackingRepository,
    private val captureCell: CaptureCellUseCase,
    private val grid: HexGrid,
) {
    suspend fun run() {
        tracking.onWalkStarted()
        try {
            coroutineScope {
                // 지난 산책의 오프라인 큐. 트랜잭션이 오프라인 판정에 시간이 걸릴 수 있어 위치 수집과 나란히 돈다.
                launch { territory.flushPending() }
                var context = WalkContext()
                locations.updates().collect { update -> context = handle(update, context) }
            }
        } finally {
            tracking.onWalkStopped()
        }
    }

    private suspend fun handle(
        update: LocationUpdate,
        context: WalkContext,
    ): WalkContext = when (update) {
        // 관측이 끊겼다 — 후보와 속도 기준 fix 는 버리고(복구 뒤 한 번의 fix 로 칠하지 않게) 마지막 셀만 남긴다.
        LocationUpdate.Unavailable -> {
            tracking.onLocation(point = null, isGpsWeak = true)
            context.copy(lastSample = null, candidateCell = null)
        }

        is LocationUpdate.Fix -> handleFix(update.sample, context)
    }

    private suspend fun handleFix(sample: LocationSample, context: WalkContext): WalkContext {
        val cell = grid.cellOf(sample.point)
        val decision = captureCell(sample, cell, context)
        val inaccurate = (decision as? CaptureDecision.Skip)?.reason == SkipReason.Inaccurate
        tracking.onLocation(sample.point, isGpsWeak = inaccurate)
        val next = context.copy(lastSample = sample)
        return when (decision) {
            CaptureDecision.Capture -> next.afterCapture(cell)

            // 새 셀의 첫 fix — 다음 fix 도 같은 셀이면 캡처한다.
            CaptureDecision.Skip(SkipReason.Unconfirmed) -> next.copy(candidateCell = cell)

            // 연속이 끊겼다(같은 셀 반복·정확도·속도·mock).
            is CaptureDecision.Skip -> next.copy(candidateCell = null)
        }
    }

    private suspend fun WalkContext.afterCapture(cell: CellId): WalkContext =
        when (territory.capture(cell)) {
            CaptureResult.Captured, CaptureResult.Queued -> {
                tracking.onCaptured()
                copy(lastCell = cell, candidateCell = null)
            }

            CaptureResult.AlreadyMine, CaptureResult.AlreadyQueued ->
                copy(lastCell = cell, candidateCell = null)

            // 일시 오류면 후보를 그대로 두어 같은 셀의 다음 위치가 다시 Capture 가 되게 한다.
            is CaptureResult.Failed -> this
        }
}
