plugins {
    alias(libs.plugins.convention.android.feature)
    alias(libs.plugins.convention.android.library.compose)
}

android {
    namespace = "com.jaychoi.eattheland.feature.map"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.model)
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(libs.kakao.maps)
    implementation(libs.androidx.compose.material.icons.core) // 내 위치 버튼 아이콘
    testImplementation(projects.core.testing)
}
