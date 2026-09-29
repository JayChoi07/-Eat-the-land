package com.jaychoi.eattheland.core.model

/** 캡처 시도 결과. Queued = 오프라인이라 로컬 큐에 넣음(나중에 자동 전송). */
sealed interface CaptureResult {
    data object Captured : CaptureResult

    data object AlreadyMine : CaptureResult

    data object Queued : CaptureResult

    data class Failed(val cause: Throwable?) : CaptureResult
}
