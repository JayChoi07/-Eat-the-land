package com.jaychoi.eattheland.core.domain

import com.jaychoi.eattheland.core.model.CaptureDecision
import com.jaychoi.eattheland.core.model.CellId
import com.jaychoi.eattheland.core.model.LocationSample
import com.jaychoi.eattheland.core.model.SkipReason
import com.jaychoi.eattheland.core.model.WalkContext
import javax.inject.Inject

/**
 * 스펙 §2 걷기 판정(v3): 정확도 ≤ 50 m ∧ 속도 ≤ 20 km/h ∧ mock 아님, 같은 셀 반복은 컷,
 * 새 셀은 같은 셀에서 fix 2번 연속일 때만 Capture(정지 상태 GPS 튐 방지).
 * 속도는 제공자 값, 없으면 직전 fix 와의 거리/시간(신호등에 선 차량은 speed 가 null 로 온다).
 * 셀 계산은 H3(Android 라이브러리)라 이 JVM 모듈에서 못 하므로 호출자가 currentCell 을 넘긴다.
 * 판정 순서는 서비스가 "GPS 약함"을 정확도 사유로 알 수 있게 mock → 정확도 → 속도 → 같은 셀 → 후보다.
 */
class CaptureCellUseCase @Inject constructor() {
    operator fun invoke(
        sample: LocationSample,
        currentCell: CellId,
        context: WalkContext,
    ): CaptureDecision = when {
        sample.isMock -> CaptureDecision.Skip(SkipReason.MockLocation)
        sample.accuracyMeters > MAX_ACCURACY_METERS -> CaptureDecision.Skip(SkipReason.Inaccurate)
        speedOf(sample, context.lastSample) > MAX_SPEED_MPS -> CaptureDecision.Skip(SkipReason.TooFast)
        currentCell == context.lastCell -> CaptureDecision.Skip(SkipReason.SameCell)
        currentCell == context.candidateCell -> CaptureDecision.Capture
        else -> CaptureDecision.Skip(SkipReason.Unconfirmed)
    }

    // 직전 fix 가 없거나 시간이 흐르지 않았으면(같은 측정의 중복) 알 수 없음 → 0(통과). 시각은 단조 시계라 역행하지 않는다.
    // speedMps 는 다른 모듈의 프로퍼티라 스마트캐스트가 안 된다 — 지역 변수로 받는다.
    private fun speedOf(sample: LocationSample, last: LocationSample?): Float {
        val measured = sample.speedMps
        val seconds = last?.let { (sample.elapsedMillis - it.elapsedMillis) / MILLIS_PER_SECOND } ?: 0.0
        return when {
            measured != null -> measured
            last == null || seconds <= 0.0 -> 0f
            else -> (distanceMeters(last.point, sample.point) / seconds).toFloat()
        }
    }

    private companion object {
        const val MAX_ACCURACY_METERS = 50f
        const val MAX_SPEED_KMH = 20f
        const val MAX_SPEED_MPS = MAX_SPEED_KMH * 1_000f / 3_600f
        const val MILLIS_PER_SECOND = 1_000.0
    }
}
