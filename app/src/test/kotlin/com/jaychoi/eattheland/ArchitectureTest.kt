// 복사 위치: :app/src/test/java/<프로젝트 패키지>/ArchitectureTest.kt
// 아래 package 선언은 복사 후 프로젝트 패키지로 바꿔 쓴다.
package com.jaychoi.eattheland

import androidx.lifecycle.ViewModel
import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.architecture.KoArchitectureCreator.assertArchitecture
import com.lemonappdev.konsist.api.architecture.Layer
import com.lemonappdev.konsist.api.ext.list.primaryConstructors
import com.lemonappdev.konsist.api.ext.list.properties
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.ext.list.withParentClassOf
import com.lemonappdev.konsist.api.ext.list.withoutAnnotationOf
import com.lemonappdev.konsist.api.ext.list.withoutNameContaining
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import org.junit.Test

/**
 * 아키텍처 규칙을 테스트로 강제한다.
 * 사용한 Konsist API 는 0.17.3 공개 소스(`lib/src/main/kotlin/com/lemonappdev/konsist/api` 아래)에서 확인했다.
 * (주석 안에 글롭 경로를 적을 때 슬래시+별표를 넣으면 Kotlin 의 중첩 블록 주석이 열려 파일이 파싱되지 않는다.)
 */
class ArchitectureTest {
    private val scope = Konsist.scopeFromProject()

    @Test
    fun `R-11-01 계층 의존은 ui에서 domain으로, domain에서 data로 단방향`() {
        scope.assertArchitecture {
            val ui = Layer("UI", "..ui..")
            val domain = Layer("Domain", "..domain..")
            val data = Layer("Data", "..data..")
            val model = Layer("Model", "..model..")

            // domain 은 선택 계층이므로 ui 가 data 를 직접 참조하는 것도 허용한다(USECASE_POLICY).
            ui.dependsOn(domain, data, model)
            domain.dependsOn(data, model)
            data.dependsOn(model)
            model.dependsOnNothing()

            // dependsOn 의 기본값은 strict = false 라 "이 방향은 허용" 만 뜻한다.
            // 허용 목록에 없다고 해서 역방향이 자동으로 잡히지는 않는다(스크래치 빌드로 확인).
            // 금지 방향은 doesNotDependOn 으로 따로 못 박아야 한다.
            data.doesNotDependOn(ui, domain)
            domain.doesNotDependOn(ui)
        }
    }

    @Test
    fun `R-11-02 Repository 인터페이스와 구현은 모두 data 계층에 둔다`() {
        scope.interfaces()
            .withNameEndingWith("Repository")
            .withoutNameContaining("Fake")
            .assertTrue { it.resideInPackage("..data..") }

        scope.classes()
            .withNameEndingWith("Repository")
            .withoutNameContaining("Fake")
            .assertTrue { it.resideInPackage("..data..") }
    }

    @Test
    fun `R-14-01 ViewModel은 HiltViewModel 애노테이션과 생성자 주입을 쓴다`() {
        val viewModels = scope.classes().withParentClassOf(ViewModel::class)

        viewModels.assertTrue { it.hasAnnotationOf(HiltViewModel::class) }
        viewModels.assertTrue { it.hasPrimaryConstructor }
        viewModels.primaryConstructors.assertTrue { it.hasAnnotationOf(Inject::class) }
    }

    @Test
    fun `R-14-02 우리가 생성하는 클래스는 필드 주입을 쓰지 않는다`() {
        // @AndroidEntryPoint / @HiltAndroidApp 은 프레임워크가 인스턴스를 만들어 생성자 주입이 불가능하다.
        scope.classes()
            .withoutAnnotationOf(AndroidEntryPoint::class, HiltAndroidApp::class)
            .properties()
            .assertFalse { it.hasAnnotationOf(Inject::class) }
    }

    @Test
    fun `R-16-01 UseCase는 public 함수가 operator invoke 하나뿐이다`() {
        scope.classes()
            .withNameEndingWith("UseCase")
            .assertTrue { useCase ->
                val publicFunctions = useCase.functions().filter { it.hasPublicOrDefaultModifier }
                publicFunctions.size == 1 &&
                    publicFunctions.first().name == "invoke" &&
                    publicFunctions.first().hasOperatorModifier
            }
    }

    @Test
    fun `R-12-01 UiState는 data class이고 ui 패키지에 둔다`() {
        scope.classes()
            .withNameEndingWith("UiState")
            .assertTrue { it.hasDataModifier && it.resideInPackage("..ui..") }
    }
}
