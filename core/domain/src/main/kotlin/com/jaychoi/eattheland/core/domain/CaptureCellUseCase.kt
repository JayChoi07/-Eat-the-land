package com.jaychoi.eattheland.core.domain

import com.jaychoi.eattheland.core.model.CaptureDecision
import com.jaychoi.eattheland.core.model.CellId
import com.jaychoi.eattheland.core.model.LocationSample
import com.jaychoi.eattheland.core.model.SkipReason
import javax.inject.Inject

/**
 * 스펙 §2 걷기 판정: 정확도 ≤ 50 m ∧ 속도 ≤ 20 km/h ∧ mock 아님, 같은 셀 반복은 컷.
 * 셀 계산은 H3(Android 라이브러리)라 이 JVM 모듈에서 못 하므로 호출자가 currentCell 을 넘긴다.
 * 판정 순서는 서비스가 "GPS 약함"을 정확도 사유로 알 수 있게 mock → 정확도 → 속도 → 같은 셀이다.
 */
class CaptureCellUseCase @Inject constructor() {
    operator fun invoke(
        sample: LocationSample,
        currentCell: CellId,
        lastCell: CellId?,
    ): CaptureDecision = when {
        sample.isMock -> CaptureDecision.Skip(SkipReason.MockLocation)
        sample.accuracyMeters > MAX_ACCURACY_METERS -> CaptureDecision.Skip(SkipReason.Inaccurate)
        (sample.speedMps ?: 0f) > MAX_SPEED_MPS -> CaptureDecision.Skip(SkipReason.TooFast)
        currentCell == lastCell -> CaptureDecision.Skip(SkipReason.SameCell)
        else -> CaptureDecision.Capture
    }

    private companion object {
        const val MAX_ACCURACY_METERS = 50f
        const val MAX_SPEED_KMH = 20f
        const val MAX_SPEED_MPS = MAX_SPEED_KMH * 1_000f / 3_600f
    }
}
