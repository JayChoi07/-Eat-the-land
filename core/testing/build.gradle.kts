// :core:testing — fake·MainDispatcherRule 같은 테스트 전용 유틸 (R-10 의존 표: testImplementation 으로만 참조).
// 소비 모듈이 testImplementation(projects.core.testing) 으로 가져가므로 여기서는 **main 소스셋**에
// JUnit·coroutines-test 를 둔다 — templates/test/MainDispatcherRule.kt 가 이 둘을 import 한다.
plugins {
    alias(libs.plugins.convention.android.library)
}

android {
    // 모듈마다 다른 namespace (R-19-03).
    namespace = "com.jaychoi.eattheland.core.testing"
}

dependencies {
    implementation(libs.junit4)
    implementation(libs.kotlinx.coroutines.test)
    implementation(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.network)
    implementation(projects.core.data)
    implementation(libs.kotlinx.coroutines.android)
}
