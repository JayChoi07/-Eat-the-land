import com.android.build.api.dsl.LibraryExtension
import io.github.takahirom.roborazzi.RoborazziExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * Compose UI 를 가진 라이브러리 모듈.
 *
 * built-in Kotlin 은 `kotlin-android` 만 대체한다. Compose 컴파일러 플러그인은 그대로 적용해야 한다.
 * 스크린샷 테스트는 Roborazzi(SCREENSHOT_LIB 확정값)이며, `verifyRoborazziDebug` 태스크를 만들기 위해
 * Roborazzi Gradle 플러그인도 함께 적용한다.
 */
class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        pluginManager.apply("io.github.takahirom.roborazzi")

        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
        extensions.configure<LibraryExtension> {
            buildFeatures.compose = true
        }
        extensions.configure<RoborazziExtension> {
            // 기본 출력 위치는 build/outputs/roborazzi 라 clean·CI 체크아웃마다 사라진다.
            // 골든 이미지가 없으면 verifyRoborazziDebug 가 "The original file was not found" 로 항상 실패한다.
            // 커밋 대상 경로로 옮긴다(record 로 만든 png 를 VCS 에 넣는다).
            outputDir.set(layout.projectDirectory.dir("src/test/screenshots"))
        }
        dependencies {
            val bom = libs.findLibrary("androidx.compose.bom").get()
            add("implementation", platform(bom))
            add("implementation", libs.findLibrary("androidx.compose.material3").get())
            add("implementation", libs.findLibrary("androidx.compose.ui.tooling.preview").get())
            add("debugImplementation", libs.findLibrary("androidx.compose.ui.tooling").get())
            // createComposeRule() 이 JVM 테스트에서 동작하려면 manifest 가 debug 소스셋에 있어야 한다(R-30-07).
            add("debugImplementation", libs.findLibrary("androidx.compose.ui.test.manifest").get())
            add("testImplementation", platform(bom))
            add("testImplementation", libs.findLibrary("androidx.compose.ui.test.junit4").get())
            add("testImplementation", libs.findLibrary("roborazzi").get())
            add("testImplementation", libs.findLibrary("roborazzi.compose").get())
            add("testImplementation", libs.findLibrary("robolectric").get())
        }
    }
}
