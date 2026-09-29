package com.jaychoi.eattheland.feature.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.jaychoi.eattheland.core.designsystem.theme.AppTheme
import com.jaychoi.eattheland.core.model.Player
import com.jaychoi.eattheland.feature.map.ui.MapScreen
import com.jaychoi.eattheland.feature.map.ui.MapUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class MapScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    private fun capture(state: MapUiState) {
        composeRule.setContent {
            AppTheme {
                MapScreen(state, onEvent = {}, onWalkToggle = {}, onOpenSettings = {}) {
                    Box(
                        Modifier.fillMaxSize().background(
                            MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage()
    }

    @Test fun with_player() = capture(MapUiState(player = Player("u", "땅주인", 0, 42)))

    @Test fun zoomed_out() = capture(
        MapUiState(player = Player("u", "땅주인", 0, 42), isZoomedOut = true),
    )

    @Test fun map_failed() = capture(
        MapUiState(player = Player("u", "땅주인", 0, 42), mapLoadFailed = true),
    )

    @Test fun tracking() = capture(
        MapUiState(
            player = Player("u", "땅주인", 0, 42),
            isTracking = true,
            walkCellCount = 3,
            pendingCount = 2,
            isGpsWeak = true,
        ),
    )

    @Test fun permission_notice() = capture(
        MapUiState(player = Player("u", "땅주인", 0, 42), showPermissionNotice = true),
    )
}
