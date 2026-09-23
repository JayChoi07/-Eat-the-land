// :core:model — 아무것도 참조하지 않는 바닥 모듈 (R-10-01, R-11-01). Android 플러그인을 붙이지 않는다.
// JVM 모듈에는 팩 컨벤션(detekt·ktlint)이 안 붙는다 — 두 모듈뿐이라 일회성으로 두고 표준 준수 보고에 적는다 (R-10-14).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}
