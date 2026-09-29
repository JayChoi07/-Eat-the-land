package com.jaychoi.eattheland.core.model

/**
 * 산책 중 판정에 필요한 직전 상태(스펙 §3 v3). WalkTracker 가 fix 마다 갱신해 CaptureCellUseCase 에 넘긴다.
 * - lastSample: 속도 미상일 때 거리/시간으로 속도를 구할 직전 fix
 * - lastCell: 마지막으로 캡처(또는 이미 내 것·큐)된 셀 — 같은 셀 반복 컷
 * - candidateCell: 새 셀에서 한 번 판정을 통과한 셀 — 다음 fix 도 같으면 캡처(2연속)
 * - lastPassedSample: 판정을 통과한(캡처·같은 셀·후보) 직전 fix — 거리 누적의 기준점. 비통과·Unavailable 이면 null
 */
data class WalkContext(
    val lastSample: LocationSample? = null,
    val lastCell: CellId? = null,
    val candidateCell: CellId? = null,
    val lastPassedSample: LocationSample? = null,
)
