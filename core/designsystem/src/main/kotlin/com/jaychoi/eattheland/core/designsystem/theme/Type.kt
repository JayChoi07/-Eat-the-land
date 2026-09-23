package com.jaychoi.eattheland.core.designsystem.theme

import androidx.compose.material3.Typography

/*
 * 타이포의 단일 출처 (R-18-10). Material 3 기본 타입 스케일에서 시작하고,
 * 브랜드 폰트가 정해지면 바꿔야 하는 스타일만 여기서 덮어쓴다.
 *   internal val AppTypography = Typography(
 *       headlineSmall = Typography().headlineSmall.copy(fontFamily = BrandFontFamily),
 *   )
 * 화면은 `MaterialTheme.typography.*` 로만 읽고 자기 TextStyle 을 만들지 않는다.
 */
internal val AppTypography = Typography()
