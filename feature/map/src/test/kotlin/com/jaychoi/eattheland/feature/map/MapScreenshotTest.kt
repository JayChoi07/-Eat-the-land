package com.jaychoi.eattheland.feature.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.jaychoi.eattheland.core.designsystem.theme.AppTheme
import com.jaychoi.eattheland.core.model.CellId
import com.jaychoi.eattheland.core.model.Player
import com.jaychoi.eattheland.core.model.WalkSummary
import com.jaychoi.eattheland.feature.map.ui.CellOwner
import com.jaychoi.eattheland.feature.map.ui.MapScreen
import com.jaychoi.eattheland.feature.map.ui.MapUiState
import com.jaychoi.eattheland.feature.map.ui.RelativeTime
import com.jaychoi.eattheland.feature.map.ui.SelectedCell
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
                MapScreen(
                    state,
                    onEvent = {},
                    onWalkToggle = {},
                    onOpenAppSettings = {},
                    onOpenRanking = {},
                    onOpenSettings = {},
                ) {
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
            distanceMeters = 1_830.0,
            elapsedMillis = 63 * 60_000L,
            pendingCount = 2,
            isGpsWeak = true,
        ),
    )

    @Test fun permission_notice() = capture(
        MapUiState(player = Player("u", "땅주인", 0, 42), showPermissionNotice = true),
    )

    @Test fun cell_card() = capture(
        MapUiState(
            player = Player("u", "땅주인", 0, 42),
            selectedCell = SelectedCell(
                CellId("8b30e1c32214fff"),
                CellOwner.Named("산책왕"),
                RelativeTime.Hours(3),
            ),
        ),
    )

    /** ModalBottomSheet 는 별도 창이라 onRoot 캡처에 안 잡힌다 — 화면 전체를 찍는다. */
    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun summary_sheet() {
        composeRule.setContent {
            AppTheme {
                MapScreen(
                    MapUiState(
                        player = Player("u", "땅주인", 0, 42),
                        summary = WalkSummary(0L, 63 * 60_000L, cells = 12, meters = 1_830.0),
                    ),
                    onEvent = {},
                    onWalkToggle = {},
                    onOpenAppSettings = {},
                    onOpenRanking = {},
                    onOpenSettings = {},
                ) {
                    Box(
                        Modifier.fillMaxSize().background(
                            MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    )
                }
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage()
    }
}
