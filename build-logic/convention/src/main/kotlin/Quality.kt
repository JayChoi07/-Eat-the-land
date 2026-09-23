import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jlleitschuh.gradle.ktlint.KtlintExtension

/**
 * 정적 분석·포맷 도구를 모듈에 붙인다.
 *
 * R-10-11 에 따라 루트 빌드 스크립트의 `subprojects { }` / `allprojects { }` 로 주입하지 않고,
 * 각 모듈이 적용하는 convention plugin 안에서 설정한다.
 * 루트 `build.gradle.kts` 는 두 플러그인을 `apply false` 로 선언해 클래스패스에만 올린다.
 */
internal fun Project.configureQuality() {
    pluginManager.apply("dev.detekt")
    pluginManager.apply("org.jlleitschuh.gradle.ktlint")

    extensions.configure<DetektExtension> {
        // detekt 2.0 확장은 전부 Gradle Property 라 대입이 아니라 set() 을 쓴다.
        buildUponDefaultConfig.set(true)
        parallel.set(true)
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        // 2.0 에는 build.maxIssues 가 없다. 실패 임계값은 failOnSeverity 로 정한다(기본값 유지).
    }

    extensions.configure<KtlintExtension> {
        // 플러그인 기본 ktlint 버전은 패치 릴리스 사이에도 바뀌므로 고정한다(FORMATTER 확정값).
        version.set(KTLINT_VERSION)
        android.set(true)
    }
}

private const val KTLINT_VERSION = "1.8.0"
