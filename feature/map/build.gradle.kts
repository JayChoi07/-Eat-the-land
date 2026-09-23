plugins {
    alias(libs.plugins.convention.android.feature)
    alias(libs.plugins.convention.android.library.compose)
}

android {
    namespace = "com.jaychoi.eattheland.feature.map"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    testImplementation(projects.core.testing)
}
