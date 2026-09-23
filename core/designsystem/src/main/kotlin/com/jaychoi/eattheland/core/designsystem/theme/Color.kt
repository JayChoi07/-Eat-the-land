package com.jaychoi.eattheland.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * 색의 단일 출처 (스펙 §6). 팔레트 상수는 private, 스킴은 internal 이라 모듈 밖으로 새지 않는다 (R-18-10).
 * 화면과 컴포넌트는 `MaterialTheme.colorScheme.onSurface` 처럼 **시맨틱 역할 이름**으로만 색을 읽고,
 * `Color(0xFF…)` 리터럴이나 밝기 분기를 직접 쓰지 않는다 (R-18-11). XML 테마에 같은 색을 다시 적지 않는다 (R-18-12).
 * 채우지 않은 역할은 Material 3 기본값을 그대로 쓴다.
 */

private val BrandPrimaryLight = Color(0xFF16A34A)
private val BrandPrimaryDark = Color(0xFF4ADE80)
private val OnPrimaryLight = Color(0xFFFFFFFF)
private val OnPrimaryDark = Color(0xFF052E16)
private val BackgroundLight = Color(0xFFF8FAFC)
private val BackgroundDark = Color(0xFF0F172A)
private val SurfaceContainerLight = Color(0xFFFFFFFF)
private val SurfaceContainerDark = Color(0xFF1E293B)
private val OnSurfaceLight = Color(0xFF0F172A)
private val OnSurfaceDark = Color(0xFFF1F5F9)
private val ErrorLight = Color(0xFFDC2626)
private val ErrorDark = Color(0xFFF87171)

internal val LightColorScheme = lightColorScheme(
    primary = BrandPrimaryLight,
    onPrimary = OnPrimaryLight,
    background = BackgroundLight,
    surface = BackgroundLight,
    surfaceContainer = SurfaceContainerLight,
    onSurface = OnSurfaceLight,
    error = ErrorLight,
)

internal val DarkColorScheme = darkColorScheme(
    primary = BrandPrimaryDark,
    onPrimary = OnPrimaryDark,
    background = BackgroundDark,
    surface = BackgroundDark,
    surfaceContainer = SurfaceContainerDark,
    onSurface = OnSurfaceDark,
    error = ErrorDark,
)
