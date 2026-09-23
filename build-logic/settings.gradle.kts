dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // ktlint-gradle 는 Maven Central 에 없고 Gradle Plugin Portal 에만 있다.
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")
