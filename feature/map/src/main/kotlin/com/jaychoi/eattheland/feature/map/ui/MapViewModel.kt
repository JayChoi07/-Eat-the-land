package com.jaychoi.eattheland.feature.map.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaychoi.eattheland.feature.map.domain.GetMapUseCase
import com.jaychoi.eattheland.feature.map.model.MapResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 기본 골격은 MVVM-UDF다. MVI가 필요하면 ui/mvi 변형으로 교체한다 (R-12-02 판단 매트릭스).
 * 최초 로드는 `init`이 아니라 UI가 부르는 멱등 `initialize()`가 시작한다 (R-12-07).
 * MapRoute의 `LaunchedEffect(viewModel) { viewModel.initialize() }`가 호출한다.
 */
@HiltViewModel
class MapViewModel @Inject constructor(
    private val getMap: GetMapUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private var initialized = false

    /** 여러 번 불려도 최초 1회만 로드한다 (R-12-07). */
    fun initialize() {
        if (initialized) return
        initialized = true
        load()
    }

    fun onEvent(event: MapEvent) {
        when (event) {
            MapEvent.Retry -> load()
        }
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = getMap()) {
                is MapResult.Success ->
                    _uiState.update { it.copy(isLoading = false, data = result.data) }

                is MapResult.Failure ->
                    _uiState.update { it.copy(isLoading = false, error = result.error) }
            }
        }
    }
}
