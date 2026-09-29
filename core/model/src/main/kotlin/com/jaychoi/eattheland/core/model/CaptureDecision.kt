package com.jaychoi.eattheland.core.model

/** 스펙 §2 걷기 판정 결과. */
sealed interface CaptureDecision {
    data object Capture : CaptureDecision

    data class Skip(val reason: SkipReason) : CaptureDecision
}

enum class SkipReason { MockLocation, Inaccurate, TooFast, SameCell }
