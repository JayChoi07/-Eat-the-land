package com.jaychoi.eattheland.feature.onboarding

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.jaychoi.eattheland.core.designsystem.theme.AppTheme
import com.jaychoi.eattheland.core.model.PlayerError
import com.jaychoi.eattheland.feature.onboarding.ui.OnboardingScreen
import com.jaychoi.eattheland.feature.onboarding.ui.OnboardingStep
import com.jaychoi.eattheland.feature.onboarding.ui.OnboardingUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class OnboardingScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    private fun capture(state: OnboardingUiState) {
        composeRule.setContent {
            AppTheme { OnboardingScreen(state, onEvent = {}, onRequestPermissions = {}) }
        }
        composeRule.onRoot().captureRoboImage()
    }

    @Test fun intro() = capture(OnboardingUiState())

    @Test fun permission() = capture(OnboardingUiState(step = OnboardingStep.Permission))

    @Test fun nickname_taken_error() = capture(
        OnboardingUiState(
            step = OnboardingStep.Nickname,
            nickname = "땅주인",
            isNicknameValid = true,
            error = PlayerError.NicknameTaken,
        ),
    )
}
