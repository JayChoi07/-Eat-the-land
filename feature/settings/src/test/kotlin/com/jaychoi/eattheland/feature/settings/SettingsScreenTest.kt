package com.jaychoi.eattheland.feature.settings

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jaychoi.eattheland.core.designsystem.theme.AppTheme
import com.jaychoi.eattheland.core.model.Player
import com.jaychoi.eattheland.feature.settings.ui.SettingsEvent
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

    /** 권한은 화면이 뜬 뒤(ON_RESUME) 읽는다 — 그 전 프레임에 "거부됨" 이 깜빡이면 안 된다. */
    @Test
    fun permission_status_is_hidden_until_read() {
        composeRule.setContent {
            AppTheme {
                SettingsScreen(
                    SettingsUiState(player = me),
                    versionName = "1.0.0",
                    onEvent = {},
                    onBack = {},
                    onOpenSystemSettings = {},
                    onOpenLicenses = {},
                )
            }
        }
        composeRule.onAllNodesWithText("거부됨").assertCountEquals(0)
        composeRule.onAllNodesWithText("허용됨").assertCountEquals(0)
        composeRule.onAllNodesWithText("설정 열기").assertCountEquals(0)
    }

    /** 편집 중에 라이선스로 나가면 편집을 접는다 — 돌아왔을 때 쓰다 만 입력이 남지 않게. */
    @Test
    fun opening_licenses_cancels_nickname_edit() {
        val events = mutableListOf<SettingsEvent>()
        var licenseCalls = 0
        composeRule.setContent {
            AppTheme {
                SettingsScreen(
                    SettingsUiState(player = me, isEditingNickname = true, nicknameInput = "산책"),
                    versionName = "1.0.0",
                    onEvent = { events += it },
                    onBack = {},
                    onOpenSystemSettings = {},
                    onOpenLicenses = { licenseCalls++ },
                )
            }
        }
        composeRule.onNodeWithText("오픈소스 라이선스").performScrollTo().performClick()
        assertEquals(listOf<SettingsEvent>(SettingsEvent.CancelEdit), events)
        assertEquals(1, licenseCalls)
    }

    @Test
    fun opening_licenses_sends_no_event_when_not_editing() {
        val events = mutableListOf<SettingsEvent>()
        composeRule.setContent {
            AppTheme {
                SettingsScreen(
                    SettingsUiState(player = me),
                    versionName = "1.0.0",
                    onEvent = { events += it },
                    onBack = {},
                    onOpenSystemSettings = {},
                    onOpenLicenses = {},
                )
            }
        }
        composeRule.onNodeWithText("오픈소스 라이선스").performScrollTo().performClick()
        assertEquals(emptyList<SettingsEvent>(), events)
    }
}
