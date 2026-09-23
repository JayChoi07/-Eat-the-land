package com.jaychoi.eattheland.core.designsystem.theme

import androidx.compose.material3.Shapes

/*
 * 모양의 단일 출처 (R-18-10). Material 3 기본값에서 시작하고 바꿀 크기만 여기서 덮어쓴다.
 *   internal val AppShapes = Shapes(medium = RoundedCornerShape(12.dp))
 * 컴포넌트는 `MaterialTheme.shapes.*` 로만 읽고 각자 RoundedCornerShape 를 만들지 않는다.
 *
 * 파일을 Color·Type 과 따로 둔 것은 이 팩의 결정이다 — 공식 산출물은 Color·Theme 두 파일까지만 보여 준다(R-18-10 근거).
 */
internal val AppShapes = Shapes()
