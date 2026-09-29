package com.jaychoi.eattheland.feature.ranking

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.jaychoi.eattheland.core.designsystem.theme.AppTheme
import com.jaychoi.eattheland.core.model.MyRank
import com.jaychoi.eattheland.core.model.RankEntry
import com.jaychoi.eattheland.core.model.RankingError
import com.jaychoi.eattheland.feature.ranking.ui.RankingScreen
import com.jaychoi.eattheland.feature.ranking.ui.RankingUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RankingScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    private val entries = listOf(
        RankEntry(1, "a", "산책왕", 1, 120),
        RankEntry(1, "b", "동네일주", 3, 120),
        RankEntry(3, "me", "나", 0, 42),
    )

    private fun capture(state: RankingUiState) {
        composeRule.setContent { AppTheme { RankingScreen(state, onEvent = {}, onBack = {}) } }
        composeRule.onRoot().captureRoboImage()
    }

    @Test fun list_with_me() = capture(
        RankingUiState(
            isLoading = false,
            entries = entries,
            me = MyRank(3, 42),
            myUid = "me",
            myNickname = "나",
        ),
    )

    @Test fun no_rank_yet() = capture(
        RankingUiState(
            isLoading = false,
            entries = entries.take(2),
            me = null,
            myUid = "me",
            myNickname = "나",
        ),
    )

    @Test fun offline_with_cache() = capture(
        RankingUiState(
            isLoading = false,
            entries = entries,
            me = MyRank(3, 42),
            myUid = "me",
            myNickname = "나",
            error = RankingError.Offline,
        ),
    )

    @Test fun empty_error() = capture(
        RankingUiState(isLoading = false, error = RankingError.Unknown),
    )
}
