// :core:domain — UseCase. Android 타입 참조 없음 (R-16-05). JVM 모듈.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(projects.core.model)
    implementation(libs.javax.inject)
    testImplementation(libs.junit4)
}
