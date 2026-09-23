import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * Compose UI 를 가진 `:app` 모듈.
 *
 * `AndroidLibraryComposeConventionPlugin` 은 `com.android.library` 를 적용하므로 앱 모듈에 쓸 수 없다.
 * built-in Kotlin 은 `kotlin-android` 만 대체하므로 Compose 컴파일러 플러그인은 그대로 적용해야 한다.
 * `com.android.application` 쪽 설정(compileSdk·JVM·품질)은 `convention.android.application` 이 하므로
 * 이 플러그인과 **짝으로** 쓴다. 단독으로 쓰면 compileSdk 가 없어 설정 단계에서 실패한다.
 *
 * 스크린샷 테스트(Roborazzi)는 화면을 소유한 `:feature:*` · `:core:designsystem` 모듈의 몫이라
 * 여기에는 Roborazzi 플러그인·의존성을 넣지 않는다.
 */
class AndroidApplicationComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
        extensions.configure<ApplicationExtension> {
            buildFeatures.compose = true
        }
        dependencies {
            val bom = libs.findLibrary("androidx.compose.bom").get()
            add("implementation", platform(bom))
            add("implementation", libs.findLibrary("androidx.compose.material3").get())
            add("implementation", libs.findLibrary("androidx.compose.ui.tooling.preview").get())
            add("debugImplementation", libs.findLibrary("androidx.compose.ui.tooling").get())
            // createComposeRule() 이 JVM 테스트에서 동작하려면 manifest 가 debug 소스셋에 있어야 한다(R-30-07).
            add("debugImplementation", libs.findLibrary("androidx.compose.ui.test.manifest").get())
        }
    }
}
