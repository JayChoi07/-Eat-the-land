package com.jaychoi.eattheland.feature.settings

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.jaychoi.eattheland.core.designsystem.theme.AppTheme
import com.jaychoi.eattheland.core.model.Player
import com.jaychoi.eattheland.core.model.PlayerError
import com.jaychoi.eattheland.feature.settings.ui.LicensesScreen
import com.jaychoi.eattheland.feature.settings.ui.SettingsScreen
import com.jaychoi.eattheland.feature.settings.ui.SettingsUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SettingsScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    private val me = Player("u", "땅주인", 0, 42)

    /** 권한은 읽은 상태(둘 다 거부)로 채운다 — 읽기 전(null)은 상태 글자가 없어 골든이 달라진다. */
    private fun capture(state: SettingsUiState) {
        val read = state.copy(
            locationGranted = state.locationGranted ?: false,
            notificationGranted = state.notificationGranted ?: false,
        )
        composeRule.setContent {
            AppTheme {
                SettingsScreen(
                    read,
                    versionName = "1.0.0-debug",
                    onEvent = {},
                    onBack = {},
                    onOpenSystemSettings = {},
                    onOpenLicenses = {},
                )
            }
        }
        composeRule.onRoot().captureRoboImage()
    }

    @Test fun default() = capture(
        SettingsUiState(player = me, locationGranted = true, notificationGranted = false),
    )

    @Test fun editing_nickname_taken() = capture(
        SettingsUiState(
            player = me,
            isEditingNickname = true,
            nicknameInput = "산책왕",
            isNicknameValid = true,
            error = PlayerError.NicknameTaken,
        ),
    )

    @Test fun delete_confirm() = capture(SettingsUiState(player = me, showDeleteConfirm = true))

    @Test fun licenses() {
        composeRule.setContent { AppTheme { LicensesScreen(onBack = {}) } }
        composeRule.onRoot().captureRoboImage()
    }
}
