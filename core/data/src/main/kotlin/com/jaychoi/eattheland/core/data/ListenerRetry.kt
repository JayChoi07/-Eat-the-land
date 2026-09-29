package com.jaychoi.eattheland.core.data

import com.jaychoi.eattheland.core.network.DataSourceException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen

private const val RETRY_BASE_MS = 5_000L
private const val RETRY_MAX_MS = 60_000L
private const val RETRY_MAX_SHIFT = 4L

/**
 * Firestore 리스너는 오류가 나면 끝난다. 간격을 늘려 가며(5초 → 최대 60초) 다시 구독한다.
 * 아직 값을 한 번도 못 냈으면 [fallback] 을 먼저 내서 화면이 첫 값을 기다리며 멈추지 않게 하고,
 * 이미 낸 값이 있으면 그대로 둔다 — 오류를 "데이터 없음"으로 바꾸지 않는다.
 */
internal fun <T> Flow<T>.retryOnListenerError(fallback: T): Flow<T> = flow {
    var emitted = false
    emitAll(
        onEach { emitted = true }.retryWhen { cause, attempt ->
            if (cause !is DataSourceException) return@retryWhen false
            if (!emitted) {
                emit(fallback)
                emitted = true
            }
            delay(retryDelayMillis(attempt))
            true
        },
    )
}

private fun retryDelayMillis(attempt: Long): Long =
    (RETRY_BASE_MS shl attempt.coerceAtMost(RETRY_MAX_SHIFT).toInt()).coerceAtMost(RETRY_MAX_MS)
