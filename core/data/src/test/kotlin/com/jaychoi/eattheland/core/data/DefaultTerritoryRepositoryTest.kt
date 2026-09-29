package com.jaychoi.eattheland.core.data

import app.cash.turbine.test
import com.jaychoi.eattheland.core.model.Cell
import com.jaychoi.eattheland.core.model.LatLngPoint
import com.jaychoi.eattheland.core.network.CellDto
import com.jaychoi.eattheland.core.network.DataSourceException
import com.jaychoi.eattheland.core.testing.FakeCellDataSource
import com.jaychoi.eattheland.core.testing.FakeHexGrid
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultTerritoryRepositoryTest {
    private val source = FakeCellDataSource()
    private val grid = FakeHexGrid()
    private val repo = DefaultTerritoryRepository(source, grid)

    private val cell = grid.cellOf(LatLngPoint(37.5661, 126.9780))
    private val other = grid.cellOf(LatLngPoint(37.5679, 126.9780))
    private val region = grid.regionOf(cell)

    private fun dto(regionId: String? = region.value, owner: String? = "u1") =
        CellDto(ownerUid = owner, ownerColor = 1, region = regionId)

    @Test
    fun `문서 ID 가 셀 ID 가 되고 필수 필드 없는 문서는 걸러진다`() = runTest {
        source.docs.value = mapOf(cell.value to dto(), other.value to dto(owner = null))
        repo.observeCells(setOf(region)).test {
            assertEquals(listOf(cell), awaitItem().map { it.id })
            assertEquals(listOf(setOf(region.value)), source.requested)
        }
    }

    @Test
    fun `격자에 없는 문서 ID 와 region 이 셀의 부모가 아닌 문서는 걸러진다`() = runTest {
        source.docs.value = mapOf(
            cell.value to dto(),
            "zz" to dto(),
            other.value to dto(regionId = "99.99_99.99"),
        )
        repo.observeCells(setOf(region)).test {
            assertEquals(listOf(cell), awaitItem().map { it.id })
        }
    }

    @Test
    fun `첫 값 전에 리스너 오류가 나면 빈 목록을 내고, 다시 구독해 복구한다`() = runTest {
        source.observeError = DataSourceException(DataSourceException.Kind.PermissionDenied)
        val received = mutableListOf<List<Cell>>()
        // 재구독 대기(delay)를 가상 시간으로 넘기려고 테스트 디스패처에서 수집한다.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repo.observeCells(setOf(region)).toList(received)
        }
        assertEquals(listOf(emptyList<Cell>()), received)

        source.observeError = null
        source.docs.value = mapOf(cell.value to dto())
        advanceTimeBy(FIRST_RETRY_MS - 1)
        assertEquals(1, source.subscriptions)
        advanceTimeBy(1)
        runCurrent()

        assertEquals(listOf(cell), received.last().map { it.id })
        assertEquals(2, source.subscriptions)
    }

    @Test
    fun `값을 낸 뒤 리스너 오류가 나면 빈 목록으로 바꾸지 않는다`() = runTest {
        source.docs.value = mapOf(cell.value to dto())
        source.observeError = DataSourceException(DataSourceException.Kind.Unknown)
        source.emitBeforeError = true
        repo.observeCells(setOf(region)).test {
            assertEquals(listOf(cell), awaitItem().map { it.id })
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `리스너 오류가 아닌 예외는 삼키지 않는다`() = runTest {
        source.observeError = IllegalStateException("bug")
        repo.observeCells(setOf(region)).test {
            assertEquals("bug", awaitError().message)
        }
    }

    private companion object {
        const val FIRST_RETRY_MS = 5_000L
    }
}
