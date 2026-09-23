// :core:designsystem — 테마(theme/)와 공통 컴포넌트(component/) (R-18-10, R-17-14). Compose 는 쓰지만 feature 는
// 아니므로 library + library.compose 조합이다 (enforcement/README.md "모듈별 플러그인 적용 예").
plugins {
    alias(libs.plugins.convention.android.library)
    alias(libs.plugins.convention.android.library.compose)
}

android {
    // 모듈마다 다른 namespace (R-19-03).
    namespace = "com.jaychoi.eattheland.core.designsystem"
}

// Compose BOM·material3·ui-tooling·Roborazzi 는 convention.android.library.compose 가 붙인다.
// 테마 4파일(templates/designsystem/)은 그 밖의 의존이 없다. 공통 컴포넌트가 생겨 더 필요해지면 여기에 적는다.
