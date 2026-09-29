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
import javax.inject.Inject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 산책 한 번: 위치 → 판정(UseCase) → 캡처(Repository) → 상태. 서비스가 lifecycleScope 에서 [run] 을 돌리고
 * 서비스가 죽으면 취소된다. 캡처는 순차(한 셀씩)라 큐 순서가 걸은 순서와 같다.
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
                var lastCell: CellId? = null
                locations.updates().collect { update -> lastCell = handle(update, lastCell) }
            }
        } finally {
            tracking.onWalkStopped()
        }
    }

    private suspend fun handle(update: LocationUpdate, lastCell: CellId?): CellId? = when (update) {
        LocationUpdate.Unavailable -> {
            tracking.onLocation(point = null, isGpsWeak = true)
            lastCell
        }

        is LocationUpdate.Fix -> handleFix(update.sample, lastCell)
    }

    private suspend fun handleFix(sample: LocationSample, lastCell: CellId?): CellId? {
        val cell = grid.cellOf(sample.point)
        val decision = captureCell(sample, cell, lastCell)
        val inaccurate = (decision as? CaptureDecision.Skip)?.reason == SkipReason.Inaccurate
        tracking.onLocation(sample.point, isGpsWeak = inaccurate)
        if (decision !is CaptureDecision.Capture) return lastCell
        return when (territory.capture(cell)) {
            CaptureResult.Captured, CaptureResult.Queued -> {
                tracking.onCaptured()
                cell
            }

            CaptureResult.AlreadyMine -> cell

            // 일시 오류면 같은 셀에서 다음 위치가 왔을 때 다시 시도한다.
            is CaptureResult.Failed -> lastCell
        }
    }
}
