// :core:datastore — Preferences DataStore 인스턴스와 그 Hilt 모듈을 가둔다 (R-15-14). 오프라인 캡처 큐 하나만 둔다.
plugins {
    alias(libs.plugins.convention.android.library)
    alias(libs.plugins.convention.android.hilt)
}

android {
    namespace = "com.jaychoi.eattheland.core.datastore"
}

dependencies {
    implementation(projects.core.common) // @IoDispatcher
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
