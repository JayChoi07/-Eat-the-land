package com.jaychoi.eattheland.feature.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey

/**
 * :app의 entryProvider { ... } 안에서 호출한다. Nav 3 1.1.7의 스코프 타입은 EntryProviderScope이고
 * `entry`는 그 스코프의 멤버 함수라 import가 필요 없다.
 *
 * :app의 NavDisplay에는 아래 데코레이터를 순서대로 넣어야 엔트리 상태가 복원되고
 * hiltViewModel()이 만든 ViewModel이 NavEntry 단위로 산다 (R-13-05).
 * 순서가 뒤바뀌면 엔트리 상태가 복원되지 않고, 둘째 줄이 없으면 화면을 떠나도 ViewModel이 정리되지 않는다.
 *   entryDecorators = listOf(
 *       rememberSaveableStateHolderNavEntryDecorator(),
 *       rememberViewModelStoreNavEntryDecorator(),
 *   )
 * 의존성은 androidx.lifecycle:lifecycle-viewmodel-navigation3 (카탈로그 별칭
 * androidx-lifecycle-viewmodel-navigation3)이며 :app 모듈에 넣는다.
 */
fun EntryProviderScope<NavKey>.mapEntry(onBack: () -> Unit) {
    entry<MapKey> { MapRoute(onBack = onBack) }
}

/**
 * ViewModel을 연결하는 Route. UI는 MapScreen이 그린다 (R-17-01).
 * 최초 로드는 ViewModel의 `init`이 아니라 여기서 시작한다 (R-12-07). `initialize()`는 멱등이라
 * 재구성·설정 변경으로 다시 불려도 로드는 한 번만 돈다.
 */
@Composable
internal fun MapRoute(
    onBack: () -> Unit,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.initialize() }
    MapScreen(uiState = uiState, onEvent = viewModel::onEvent, onBack = onBack)
}
