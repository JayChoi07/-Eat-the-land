import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * `:feature:*` 모듈. library + hilt 컨벤션을 합성하고 화면 계층 공통 의존성을 얹는다.
 * FEATURE_MODULE_SPLIT 확정값에 따라 단일 feature 모듈로 시작한다.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("convention.android.library")
        pluginManager.apply("convention.android.hilt")
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
        dependencies {
            add("implementation", libs.findLibrary("androidx.hilt.lifecycle.viewmodel.compose").get())
            add("implementation", libs.findLibrary("androidx.lifecycle.runtime.compose").get())
            add("implementation", libs.findLibrary("androidx.lifecycle.viewmodel.compose").get())
            add("implementation", libs.findLibrary("androidx.navigation3.runtime").get())
            add("implementation", libs.findLibrary("androidx.navigation3.ui").get())
            add("implementation", libs.findLibrary("kotlinx.serialization.json").get())
            add("testImplementation", libs.findLibrary("junit4").get())
            add("testImplementation", libs.findLibrary("kotlinx.coroutines.test").get())
            add("testImplementation", libs.findLibrary("turbine").get())
            add("testImplementation", libs.findLibrary("konsist").get())
        }
    }
}
