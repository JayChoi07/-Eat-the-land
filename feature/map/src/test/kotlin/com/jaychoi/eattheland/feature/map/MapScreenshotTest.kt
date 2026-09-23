package com.jaychoi.eattheland.feature.map

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.jaychoi.eattheland.core.designsystem.theme.AppTheme
import com.jaychoi.eattheland.feature.map.ui.MapScreen
import com.jaychoi.eattheland.feature.map.ui.MapUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * `@Config(sdk = [35])`가 필요한 이유 두 가지. 둘 다 실빌드로 확인한 제약이다.
 * 1. Robolectric은 targetSdk가 자기 지원 상한보다 높으면 실행을 거부한다
 *    (`Package targetSdkVersion=37 > maxSdkVersion=36`). 팩의 compileSdk/targetSdk는 37이다.
 * 2. Robolectric 4.16.1의 SDK 36 이미지는 Java 21을 요구한다(`SDK 36 requires Java 21`).
 *    팩의 JDK 확정값은 17이므로 JDK 17에서 도는 최신 SDK인 35로 내린다.
 * 테스트 JVM을 JDK 21로 올릴 수 있으면 36까지 쓸 수 있고, Robolectric이 37을 지원하면 이 줄을 지운다.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class MapScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun default_state() {
        composeRule.setContent {
            AppTheme { MapScreen(MapUiState(), onEvent = {}, onBack = {}) }
        }
        composeRule.onRoot().captureRoboImage()
    }

    @Test
    fun loading_state() {
        composeRule.setContent {
            AppTheme { MapScreen(MapUiState(isLoading = true), onEvent = {}, onBack = {}) }
        }
        composeRule.onRoot().captureRoboImage()
    }
}
