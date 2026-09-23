package com.jaychoi.eattheland.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * 색의 단일 출처. 팔레트 상수는 private, 스킴은 internal 이라 모듈 밖으로 새지 않는다 (R-18-10).
 * 화면과 컴포넌트는 `MaterialTheme.colorScheme.onSurface` 처럼 **시맨틱 역할 이름**으로만 색을 읽고,
 * `Color(0xFF…)` 리터럴이나 밝기 분기를 직접 쓰지 않는다 (R-18-11).
 *
 * 아래 네 값은 자리표시자다. 브랜드 색이 정해지면 이 파일에서만 바꾼다 — XML 테마에 같은 색을 다시 적지 않는다 (R-18-12).
 * 채우지 않은 역할은 Material 3 기본값을 그대로 쓴다.
 */

private val BrandPrimaryLight = Color(0xFF476810)
private val BrandPrimaryDark = Color(0xFFACD370)
private val BrandSecondaryLight = Color(0xFF586249)
private val BrandSecondaryDark = Color(0xFFBFCBAD)

internal val LightColorScheme = lightColorScheme(
    primary = BrandPrimaryLight,
    secondary = BrandSecondaryLight,
)

internal val DarkColorScheme = darkColorScheme(
    primary = BrandPrimaryDark,
    secondary = BrandSecondaryDark,
)
