package com.jaychoi.eattheland.feature.map

import app.cash.turbine.test
import com.jaychoi.eattheland.core.testing.MainDispatcherRule
import com.jaychoi.eattheland.feature.map.domain.GetMapUseCase
import com.jaychoi.eattheland.feature.map.model.Map
import com.jaychoi.eattheland.feature.map.model.MapError
import com.jaychoi.eattheland.feature.map.model.MapResult
import com.jaychoi.eattheland.feature.map.ui.MapEvent
import com.jaychoi.eattheland.feature.map.ui.MapViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** 로드는 `init`이 아니라 `initialize()`가 시작하므로 (R-12-07) 테스트가 직접 부른다. */
class MapViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeMapRepository()

    private fun viewModel() = MapViewModel(GetMapUseCase(repository))

    @Test
    fun `성공 시 data가 채워지고 로딩이 끝난다`() = runTest {
        val viewModel = viewModel()
        viewModel.initialize()
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(Map(id = "1", name = "fake"), state.data)
            assertEquals(false, state.isLoading)
            assertNull(state.error)
        }
    }

    @Test
    fun `실패 시 error가 채워진다`() = runTest {
        repository.result = MapResult.Failure(MapError.NotFound)
        val viewModel = viewModel()
        viewModel.initialize()
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(MapError.NotFound, state.error)
            assertNull(state.data)
        }
    }

    @Test
    fun `Retry 이벤트는 다시 로드한다`() = runTest {
        val viewModel = viewModel()
        viewModel.initialize()
        viewModel.onEvent(MapEvent.Retry)
        assertEquals(2, repository.callCount)
    }
}
