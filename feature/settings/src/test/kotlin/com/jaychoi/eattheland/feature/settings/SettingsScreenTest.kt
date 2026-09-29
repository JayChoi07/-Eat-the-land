package com.jaychoi.eattheland.feature.settings

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jaychoi.eattheland.core.designsystem.theme.AppTheme
import com.jaychoi.eattheland.core.model.Player
import com.jaychoi.eattheland.feature.settings.ui.SettingsScreen
import com.jaychoi.eattheland.feature.settings.ui.SettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SettingsScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private val me = Player("u", "땅주인", 0, 42)

    /** 삭제가 진행 중이면 뒤로가기(앱바·시스템) 둘 다 막힌다 — 배치는 이미 갔고 Auth 삭제·완료 이동이 남아 있다. */
    @Test
    fun back_is_blocked_while_deleting() {
        var backCalls = 0
        lateinit var dispatcher: OnBackPressedDispatcher
        composeRule.setContent {
            dispatcher = requireNotNull(LocalOnBackPressedDispatcherOwner.current)
                .onBackPressedDispatcher
            AppTheme {
                SettingsScreen(
                    SettingsUiState(player = me, isDeleting = true),
                    versionName = "1.0.0",
                    onEvent = {},
                    onBack = { backCalls++ },
                    onOpenSystemSettings = {},
                    onOpenLicenses = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("뒤로").performClick()
        composeRule.runOnUiThread { dispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertEquals(0, backCalls)
    }

    @Test
    fun back_works_when_not_deleting() {
        var backCalls = 0
        composeRule.setContent {
            AppTheme {
                SettingsScreen(
                    SettingsUiState(player = me),
                    versionName = "1.0.0",
                    onEvent = {},
                    onBack = { backCalls++ },
                    onOpenSystemSettings = {},
                    onOpenLicenses = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("뒤로").performClick()
        assertEquals(1, backCalls)
    }
}
