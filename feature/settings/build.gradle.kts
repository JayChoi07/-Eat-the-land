plugins {
    alias(libs.plugins.convention.android.feature)
    alias(libs.plugins.convention.android.library.compose)
}

android {
    namespace = "com.jaychoi.eattheland.feature.settings"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.model)
    implementation(projects.core.data)
    implementation(projects.core.domain)
    implementation(projects.core.designsystem)
    implementation(libs.androidx.compose.material.icons.core) // 뒤로가기 화살표
    testImplementation(projects.core.testing)
}
