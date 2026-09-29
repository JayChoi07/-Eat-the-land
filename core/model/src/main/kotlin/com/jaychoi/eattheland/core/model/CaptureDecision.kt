package com.jaychoi.eattheland.core.model

/** 스펙 §2 걷기 판정 결과. */
sealed interface CaptureDecision {
    data object Capture : CaptureDecision

    data class Skip(val reason: SkipReason) : CaptureDecision
}

/** Unconfirmed: 새 셀의 첫 fix — 다음 fix 도 같은 셀이면 캡처한다(v3 2연속). */
enum class SkipReason { MockLocation, Inaccurate, TooFast, SameCell, Unconfirmed }
