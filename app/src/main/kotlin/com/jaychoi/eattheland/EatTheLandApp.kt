package com.jaychoi.eattheland

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass
import com.jaychoi.eattheland.feature.map.ui.MapKey
import com.jaychoi.eattheland.feature.map.ui.mapEntry

/**
 * 앱 루트 컴포저블. :app 이 백스택과 feature 조합을 소유한다 (R-10-08, R-13-03).
 *
 * 세 가지를 한 곳에서만 한다.
 * 1. 백스택 — `rememberNavBackStack` 호출은 앱 전체에 이 한 줄이고, 조작은 [Navigator] 로만 한다 (R-13-03).
 * 2. 인셋 — `Scaffold` 의 `innerPadding` 을 적용하고 `consumeWindowInsets` 로 소비를 표시한다. 아래 화면이 같은
 *    인셋을 다시 더하지 않는다 (R-18-08).
 * 3. 창 크기 — `windowSizeClass` 를 여기서 한 번 읽는다. 화면이 늘면 "레일을 쓸지" 같은 **결정 결과**를
 *    파라미터로 내려보내고, 화면이 스스로 창을 재지 않게 한다 (R-18-13).
 *      val useNavRail = windowSizeClass
 *          .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
 *
 * feature 를 하나 더 붙일 때는 아래 `entryProvider` 블록에 그 feature 의 엔트리 빌더 확장 함수를 한 줄 더한다
 * (R-13-04). feature 끼리는 서로의 키를 모르므로 이동 목적지는 여기서 콜백으로 잇는다 (R-10-02, R-13-02).
 */
@Suppress("UnusedParameter")
@Composable
fun EatTheLandApp(
    modifier: Modifier = Modifier,
    windowSizeClass: WindowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass,
) {
    val backStack = rememberNavBackStack(MapKey)
    val navigator = remember(backStack) { Navigator(backStack) }

    Scaffold(modifier = modifier) { innerPadding ->
        NavDisplay(
            backStack = backStack,
            // Scaffold 는 인셋을 소비하지 않으므로 여기서 소비를 표시한다 (R-18-08).
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
            onBack = { navigator.goBack() },
            // 첫 항목이 SaveableStateHolder 여야 엔트리 상태가 복원되고, 둘째 줄이 있어야
            // ViewModel 이 NavEntry 단위로 살고 정리된다 (R-13-05).
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            // when 분기가 아니라 entryProvider DSL 로 키→콘텐츠를 잇는다 (R-13-04).
            entryProvider = entryProvider {
                mapEntry(onBack = { navigator.goBack() })
            },
        )
    }
}
