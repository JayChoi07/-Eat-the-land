import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/**
 * 모든 Android 모듈이 공유하는 컴파일 설정.
 *
 * AGP 9 주의점 두 가지:
 * 1. `CommonExtension` 의 타입 파라미터가 제거됐다. `CommonExtension<*, *, *, *, *, *>` 는 컴파일되지 않는다.
 * 2. 블록 메서드가 `CommonExtension` 에서 각 확장(ApplicationExtension/LibraryExtension)으로 옮겨갔다.
 *    공통 코드에서는 `compileOptions { }` 대신 프로퍼티 접근 + `apply { }` 를 쓴다.
 */
internal fun Project.configureKotlinAndroid(ext: CommonExtension) {
    ext.compileSdk = COMPILE_SDK
    ext.defaultConfig.minSdk = MIN_SDK
    ext.compileOptions.apply {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    configureKotlinCompiler()
    configureUnitTestTasks()
}

/**
 * Gradle 9 는 "테스트 소스는 있는데 실행된 테스트가 0개" 이면 태스크를 실패시킨다.
 * Hilt 가 모듈마다 unit test 소스셋에 생성 코드를 넣기 때문에, 아직 테스트를 안 쓴 모듈까지
 * 이 검사에 걸려 루트에서 `./gradlew testDebugUnitTest` 를 못 돌린다. 그래서 끈다.
 */
private fun Project.configureUnitTestTasks() {
    tasks.withType<Test>().configureEach {
        failOnNoDiscoveredTests.set(false)
    }
}

/**
 * Kotlin 컴파일러 옵션.
 *
 * AGP 9 는 built-in Kotlin 이 기본이라 `org.jetbrains.kotlin.android` 를 적용하지 않는다.
 * 컴파일러 옵션은 `android.kotlinOptions` 가 아니라 `kotlin.compilerOptions` 로 설정한다.
 */
private fun Project.configureKotlinCompiler() {
    extensions.configure<KotlinAndroidProjectExtension> {
        compilerOptions {
            // built-in Kotlin 은 compileOptions.targetCompatibility 를 따라가지만 명시해 고정한다.
            jvmTarget.set(JvmTarget.JVM_17)
            allWarningsAsErrors.set(true)
        }
    }
}

// compose BOM 2026.08.00(compose 1.12.0) · lifecycle 2.11.0 · androidx.hilt 1.4.0 의 AAR 메타데이터가
// "compile against API 37 or later" 를 요구한다. 36 으로 두면 checkDebugAarMetadata 에서 빌드가 깨진다.
// AGP 9.4.0 의 최대 지원 API 도 37 이다.
internal const val COMPILE_SDK = 37
internal const val MIN_SDK = 26
