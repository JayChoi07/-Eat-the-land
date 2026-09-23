import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * 모든 `:core:*` / `:feature:*` 라이브러리 모듈의 기본 설정.
 *
 * AGP 9 에서 구 DSL 클래스(`com.android.build.gradle.LibraryExtension`)는 더 이상 노출되지 않는다.
 * 반드시 `com.android.build.api.dsl.LibraryExtension` 을 쓴다.
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> {
            configureKotlinAndroid(this)
            defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            // Robolectric/Roborazzi 가 android 리소스를 읽을 수 있어야 한다.
            testOptions.unitTests.isIncludeAndroidResources = true
        }
        configureQuality()
    }
}
