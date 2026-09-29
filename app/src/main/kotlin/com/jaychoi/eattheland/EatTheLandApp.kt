package com.jaychoi.eattheland

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass
import com.jaychoi.eattheland.feature.map.ui.MapKey
import com.jaychoi.eattheland.feature.map.ui.mapEntry
import com.jaychoi.eattheland.feature.onboarding.ui.OnboardingKey
import com.jaychoi.eattheland.feature.onboarding.ui.onboardingEntry
import com.jaychoi.eattheland.tracking.LocationTrackingService
import com.jaychoi.eattheland.ui.AppRootViewModel

/**
 * 앱 루트. 백스택·feature 조합을 :app 이 소유한다 (R-10-08, R-13-03).
 * 프로필 유무는 [AppRootViewModel] 이 정하고, 첫 값이 오기 전에는 MainActivity 가 스플래시를 유지한다 (R-18-04).
 * 창 크기는 여기서 한 번 읽는다 — 화면이 늘면 결정 결과만 파라미터로 내려보낸다 (R-18-13).
 */
@Suppress("UnusedParameter")
@Composable
fun EatTheLandApp(
    modifier: Modifier = Modifier,
    windowSizeClass: WindowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass,
    viewModel: AppRootViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    if (uiState.isLoading) return

    val startKey: NavKey = if (uiState.hasProfile) MapKey else OnboardingKey
    val backStack = rememberNavBackStack(startKey)
    val navigator = remember(backStack) { Navigator(backStack) }
    // startKey 는 백스택을 처음 만들 때만 쓰인다. 프로필이 그 뒤에 확인되면(늦은 첫 응답, 복원된 백스택)
    // 온보딩에 남지 않게 지도로 바꾼다 — 온보딩 완료 조건은 프로필 존재다 (스펙 §5).
    LaunchedEffect(uiState.hasProfile) {
        if (uiState.hasProfile) navigator.replaceAllIfPresent(from = OnboardingKey, to = MapKey)
    }

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
                onboardingEntry(onCompleted = { navigator.replaceAll(MapKey) })
                // 산책 추적은 :app 의 FGS. feature 는 시작/종료 콜백만 안다.
                mapEntry(
                    onStartWalk = { LocationTrackingService.start(context) },
                    onStopWalk = { LocationTrackingService.stop(context) },
                )
            },
        )
    }
}
