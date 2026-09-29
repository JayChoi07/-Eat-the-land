package com.jaychoi.eattheland.feature.settings.ui

/** 라이선스 표기 대상. 이름·라이선스·URL 은 번역 대상이 아닌 고유명사라 strings.xml 이 아니다. */
data class OpenSourceLicense(val name: String, val license: String, val url: String)

internal val openSourceLicenses = listOf(
    OpenSourceLicense(
        "Kakao Map SDK for Android",
        "Kakao Developers 이용약관",
        "https://developers.kakao.com/terms",
    ),
    OpenSourceLicense("H3 (h3-android)", "Apache License 2.0", "https://github.com/uber/h3-java"),
    OpenSourceLicense(
        "Firebase Android SDK",
        "Apache License 2.0",
        "https://github.com/firebase/firebase-android-sdk",
    ),
    OpenSourceLicense(
        "AndroidX · Jetpack Compose",
        "Apache License 2.0",
        "https://developer.android.com/jetpack",
    ),
    OpenSourceLicense(
        "Kotlin · kotlinx.coroutines",
        "Apache License 2.0",
        "https://github.com/JetBrains/kotlin",
    ),
    OpenSourceLicense("Dagger · Hilt", "Apache License 2.0", "https://github.com/google/dagger"),
)
