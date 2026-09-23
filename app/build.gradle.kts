// :app — 진입점 하나. 루트 네비게이션과 feature 조합을 소유한다 (R-10-08).
// 플러그인 조합은 enforcement/README.md "모듈별 플러그인 적용 예"와 같다. application.compose 는
// library.compose 로 대체할 수 없다(그쪽은 com.android.library 를 적용한다) — R-10-10.
//
// 아래 의존은 전부 gradle/libs.versions.toml 의 별칭으로만 참조한다 (R-10-12).
// 앱 셸 3개(activity-compose · core-splashscreen · material3 adaptive)는
// enforcement/build-logic/libs.versions.toml.snippet 의 "앱 셸" 그룹에 있다 — 설치 3단계에서 함께 병합된다.
plugins {
    alias(libs.plugins.convention.android.application)
    alias(libs.plugins.convention.android.application.compose)
    alias(libs.plugins.convention.android.hilt)
    alias(libs.plugins.google.services)
}

android {
    // namespace 와 applicationId 를 둘 다 적고, applicationId 는 출시 후 바꾸지 않는다 (R-19-03).
    namespace = "com.jaychoi.eattheland"

    defaultConfig {
        applicationId = "com.jaychoi.eattheland"
        // compileSdk 는 컨벤션 플러그인 상수에서 오고, targetSdk 는 :app 에 같은 값으로 명시한다 (R-19-01).
        targetSdk = 37
        // versionCode·versionName 부여 방식은 19가 규칙으로 정하지 않는다(출처 침묵). 프로젝트 지침에서 정한다.
        versionCode = 1
        versionName = "1.0.0"
    }

    // buildType 은 debug·release 둘뿐이다 (R-19-04).
    buildTypes {
        debug {
            // 릴리스 빌드와 한 기기에 함께 깔리게 한다 (R-19-05). isDebuggable·서명은 도구 기본값이라 적지 않는다.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // AGP 9.3+ 의 optimization DSL 한 줄 (R-19-04).
            // isMinifyEnabled·isShrinkResources·proguardFiles 를 따로 적지 않는다.
            optimization { enable = true }
        }
    }

    // productFlavors 블록은 두지 않는다 — 배포 단위가 갈릴 때만 만든다 (R-19-06).

    // BuildConfig 는 실제로 필드를 둘 때만 켠다 (R-19-13). 켜면 그 값은 :app 만 소유하고
    // :core:* · :feature:* 에는 Hilt 로 넘긴다 (R-19-14).
    // buildFeatures { buildConfig = true }
    // defaultConfig { buildConfigField("String", "BASE_URL", "\"https://api.example.com\"") }
}

// 서명 자료는 루트 keystore.properties 에서 읽고 커밋하지 않는다 (R-19-12). :app 만 쓰는 설정이라
// 컨벤션 플러그인으로 올리지 않는다 (R-10-14). 실제 릴리스를 낼 때 signingConfigs 를 여기에 추가한다.

dependencies {
    // :app 만 feature 를 안다. feature 끼리는 서로 의존하지 않는다 (R-10-02, R-10-08).
    implementation(projects.core.designsystem)
    implementation(projects.feature.map)

    // 앱 셸이 직접 부르는 API. material3·tooling·BOM 은 convention.android.application.compose 가 붙인다.
    // ComponentActivity·setContent·enableEdgeToEdge (R-18-06, R-18-07)
    implementation(libs.androidx.activity.compose)
    // installSplashScreen() (R-18-03)
    implementation(libs.androidx.core.splashscreen)
    // currentWindowAdaptiveInfo() (R-18-13)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    // rememberViewModelStoreNavEntryDecorator (R-13-05)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    // Firebase 초기화만 :app 이 한다(App Startup Initializer). Auth·Firestore 사용은 :core:network 에.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.common)
    implementation(libs.androidx.startup.runtime)

    // Konsist 아키텍처 테스트는 :app 의 test 소스셋에 둔다(enforcement/README.md 설치 6단계).
    testImplementation(libs.junit4)
    testImplementation(libs.konsist)
}
