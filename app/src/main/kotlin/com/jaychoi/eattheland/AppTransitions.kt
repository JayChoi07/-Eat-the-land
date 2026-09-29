package com.jaychoi.eattheland

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

/**
 * 앱 전체 화면 전환(사용자 결정 2026-09-29): Activity 전환처럼 push 는 오른쪽에서 들어오며 기존 화면을
 * 왼쪽으로 밀어내고, pop 은 왼쪽 화면이 돌아오며 현재 화면이 오른쪽으로 나간다. NavDisplay 기본(크로스페이드)
 * 대신 이 셋을 항상 넘긴다. 예측 뒤로가기도 같은 방향.
 */
internal object AppTransitions {
    private const val DURATION_MS = 300
    private const val PARALLAX_DIVISOR = 3

    fun <T> push(): AnimatedContentTransitionScope<T>.() -> ContentTransform = {
        slideInHorizontally(tween(DURATION_MS)) { width -> width } togetherWith
            slideOutHorizontally(tween(DURATION_MS)) { width -> -width / PARALLAX_DIVISOR }
    }

    fun <T> pop(): AnimatedContentTransitionScope<T>.() -> ContentTransform = {
        slideInHorizontally(tween(DURATION_MS)) { width -> -width / PARALLAX_DIVISOR } togetherWith
            slideOutHorizontally(tween(DURATION_MS)) { width -> width }
    }

    fun <T> predictivePop(): AnimatedContentTransitionScope<T>.(Int) -> ContentTransform = {
        slideInHorizontally(tween(DURATION_MS)) { width -> -width / PARALLAX_DIVISOR } togetherWith
            slideOutHorizontally(tween(DURATION_MS)) { width -> width }
    }
}
