package com.jaychoi.eattheland

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * `@HiltAndroidApp` 선언만 갖는다 (R-18-01). onCreate 를 열어 디스크·네트워크·DB 를 건드리거나
 * 세션 상태를 필드로 들고 있지 않는다 — 필요한 객체는 Hilt 가 첫 주입 시점에 만든다.
 *
 * 시작 시 반드시 돌아야 하는 초기화가 생기면 `Application.onCreate` 가 아니라
 * 18의 "초기화 배치 결정 매트릭스"로 자리를 정하고, 순서는 `Initializer.dependencies()` 로만 표현한다 (R-18-02).
 *
 * Hilt 진입점은 이 클래스와 [MainActivity] 둘뿐이다 (R-14-03).
 */
@HiltAndroidApp
class EatTheLandApplication : Application()
