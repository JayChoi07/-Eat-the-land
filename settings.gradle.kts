// 루트 settings. build-logic 을 included build 로 등록하고(R-10-09) 모듈 그래프를 연다(R-10-01).
// pluginManagement 의 저장소에 gradlePluginPortal() 이 없으면 ktlint-gradle 해석이 실패한다(R-10-09 체크 항목).
// enableFeaturePreview 줄은 템플릿·문서가 쓰는 projects.* 접근자에 필요하다(enforcement/README.md 설치 2단계).
pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "eattheland"

// 진입점 하나 + 첫 화면이 실제로 쓰는 core 모듈만 연다. "언젠가 쓸 것 같아서" 미리 만들지 않는다 (R-10-04).
// :core:model · :core:data · :core:network · :core:database · :core:datastore · :core:domain 은
// 두 번째 사용처가 생기는 시점에 추가한다 (R-10-01 이 정한 이름 목록 안에서만).
include(":app")
include(":core:model")
include(":core:common")
include(":core:network")
include(":core:data")
include(":core:domain")
include(":core:designsystem")
include(":core:testing")
include(":feature:map")
