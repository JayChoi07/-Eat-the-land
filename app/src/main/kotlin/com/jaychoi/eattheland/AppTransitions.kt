package com.jaychoi.eattheland

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

/**
 * 앱 전체 화면 전환(사용자 결정 2026-09-29): Activity 전환처럼 push 는 새 화면이 오른쪽에서 들어와 위를 덮고,
 * pop 은 위 화면이 오른쪽으로 빠지며 아래 화면이 드러난다. 아래 화면은 움직이지 않는다 — 지도의 카카오
 * MapView 가 SurfaceView 라 컴포즈 오프셋을 따라오지 못하고 잘려 보이기 때문. z 순서(위 화면이 항상 위)는
 * NavDisplay 가 push/pop 에 맞춰 정한다. 예측 뒤로가기도 같은 방향.
 */
internal object AppTransitions {
    private const val DURATION_MS = 300

    fun <T> push(): AnimatedContentTransitionScope<T>.() -> ContentTransform = {
        slideInHorizontally(tween(DURATION_MS)) { width -> width } togetherWith
            ExitTransition.KeepUntilTransitionsFinished
    }

    fun <T> pop(): AnimatedContentTransitionScope<T>.() -> ContentTransform = {
        EnterTransition.None togetherWith slideOutToRight()
    }

    fun <T> predictivePop(): AnimatedContentTransitionScope<T>.(Int) -> ContentTransform = {
        EnterTransition.None togetherWith slideOutToRight()
    }

    private fun slideOutToRight(): ExitTransition =
        slideOutHorizontally(tween(DURATION_MS)) { width -> width }
}
