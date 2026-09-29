package com.jaychoi.eattheland.tracking

import com.jaychoi.eattheland.core.common.Clock
import com.jaychoi.eattheland.core.data.tracking.TrackingRepository
import com.jaychoi.eattheland.core.data.walk.WalkRepository
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * 산책의 시작과 끝(스펙 C §8). 끝은 서비스 취소 경로에서도 불리므로 저장은 NonCancellable 로 한 번 시도한다.
 * 저장 결과는 보지 않는다 — 실패는 버린다(사용자 결정 6). WalkTracker 생성자 수(≤ 6)를 지키려고 묶었다.
 */
class WalkSession @Inject constructor(
    private val tracking: TrackingRepository,
    private val walks: WalkRepository,
    private val clock: Clock,
) {
    fun start() = tracking.onWalkStarted(clock.nowMillis())

    suspend fun finish() {
        tracking.onWalkStopped(clock.nowMillis())
        val summary = tracking.state.value.lastSummary ?: return
        withContext(NonCancellable) { walks.save(summary) }
    }
}
