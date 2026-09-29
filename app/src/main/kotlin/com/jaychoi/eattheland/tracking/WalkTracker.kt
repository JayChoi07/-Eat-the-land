package com.jaychoi.eattheland.tracking

import com.jaychoi.eattheland.core.common.grid.HexGrid
import com.jaychoi.eattheland.core.data.TerritoryRepository
import com.jaychoi.eattheland.core.data.location.LocationRepository
import com.jaychoi.eattheland.core.data.tracking.TrackingRepository
import com.jaychoi.eattheland.core.domain.CaptureCellUseCase
import com.jaychoi.eattheland.core.domain.distanceMeters
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
    private val session: WalkSession,
) {
    suspend fun run() {
        session.start()
        try {
            coroutineScope {
                // 지난 산책의 오프라인 큐. 트랜잭션이 오프라인 판정에 시간이 걸릴 수 있어 위치 수집과 나란히 돈다.
                launch { territory.flushPending() }
                var context = WalkContext()
                locations.updates().collect { update -> context = handle(update, context) }
            }
        } finally {
            session.finish()
        }
    }

    private suspend fun handle(
        update: LocationUpdate,
        context: WalkContext,
    ): WalkContext = when (update) {
        // 관측이 끊겼다 — 후보와 속도 기준 fix 는 버리고(복구 뒤 한 번의 fix 로 칠하지 않게) 마지막 셀만 남긴다.
        LocationUpdate.Unavailable -> {
            tracking.onLocation(point = null, isGpsWeak = true)
            context.copy(lastSample = null, candidateCell = null, lastPassedSample = null)
        }

        is LocationUpdate.Fix -> handleFix(update.sample, context)
    }

    private suspend fun handleFix(sample: LocationSample, context: WalkContext): WalkContext {
        val cell = grid.cellOf(sample.point)
        val decision = captureCell(sample, cell, context)
        val inaccurate = (decision as? CaptureDecision.Skip)?.reason == SkipReason.Inaccurate
        tracking.onLocation(sample.point, isGpsWeak = inaccurate)
        val next = context.copy(
            lastSample = sample,
            lastPassedSample = passedSample(decision, sample, context),
        )
        return when (decision) {
            CaptureDecision.Capture -> next.afterCapture(cell)

            // 새 셀의 첫 fix — 다음 fix 도 같은 셀이면 캡처한다.
            CaptureDecision.Skip(SkipReason.Unconfirmed) -> next.copy(candidateCell = cell)

            // 연속이 끊겼다(같은 셀 반복·정확도·속도·mock).
            is CaptureDecision.Skip -> next.copy(candidateCell = null)
        }
    }

    // 거리(스펙 C 결정 4): 게이트(정확도·속도·mock)를 통과한 fix 사이만 더한다. 비통과 뒤 첫 통과 fix 는 기준만 잡는다.
    private fun passedSample(
        decision: CaptureDecision,
        sample: LocationSample,
        context: WalkContext,
    ): LocationSample? {
        if (!decision.passedGate()) return null
        context.lastPassedSample?.let { previous ->
            tracking.onDistance(distanceMeters(previous.point, sample.point))
        }
        return sample
    }

    private fun CaptureDecision.passedGate(): Boolean = when (this) {
        CaptureDecision.Capture -> true
        is CaptureDecision.Skip -> reason == SkipReason.SameCell || reason == SkipReason.Unconfirmed
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
