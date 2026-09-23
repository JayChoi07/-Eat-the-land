package com.jaychoi.eattheland.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * 테마 패키지의 **유일한 공개 API** (R-18-10). 앱 진입점과 Preview·스크린샷 테스트가 이것을 쓴다.
 * 버튼·카드 같은 공통 컴포넌트는 같은 모듈의 `component/` 패키지에서 따로 공개한다 (R-17-14).
 * feature 나 화면에서 `MaterialTheme(...)` 을 다시 호출하지 않는다.
 *
 * 다크는 `isSystemInDarkTheme()` 기본값으로 받는다 (R-18-11). 다이나믹 컬러는 브랜드 색을 포기해도 되는
 * 앱에서만 켠다 — 켤 때는 `dynamicLightColorScheme(context)` / `dynamicDarkColorScheme(context)` 로 분기한다.
 *
 * 테마가 실어 내려야 할 커스텀 값(간격 토큰 등)이 생기면 `CompositionLocal` 로 여기서 제공한다.
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
