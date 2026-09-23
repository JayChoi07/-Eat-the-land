// :core:common — 어디에도 의존하지 않는 순수 공용 모듈 (R-10-03, R-10 의존 표).
// 여기 오는 코드는 templates/di/Dispatchers.kt(Hilt 모듈 + 코루틴 디스패처 qualifier) 같은 것뿐이다.
// Compose 는 쓰지 않으므로 library.compose 를 붙이지 않는다 (R-19-13).
plugins {
    alias(libs.plugins.convention.android.library)
    alias(libs.plugins.convention.android.hilt) // Dispatchers.kt 의 @Module 을 위해
}

android {
    // 모듈마다 다른 namespace (R-19-03). applicationId 는 두지 않는다.
    namespace = "com.jaychoi.eattheland.core.common"
}

dependencies {
    // Dispatchers.kt — CoroutineDispatcher·Dispatchers. hilt 컨벤션이 hilt-android 를 붙인다.
    implementation(libs.kotlinx.coroutines.android)
    implementation(projects.core.model)
    implementation(libs.h3.android)
    testImplementation(projects.core.testing)
    testImplementation(libs.junit4)
}
