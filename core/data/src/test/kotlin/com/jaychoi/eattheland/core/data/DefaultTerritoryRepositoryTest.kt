package com.jaychoi.eattheland.core.data

import app.cash.turbine.test
import com.jaychoi.eattheland.core.common.Clock
import com.jaychoi.eattheland.core.data.sync.PendingCaptureQueue
import com.jaychoi.eattheland.core.datastore.PendingCapture
import com.jaychoi.eattheland.core.model.CaptureResult
import com.jaychoi.eattheland.core.model.Cell
import com.jaychoi.eattheland.core.model.LatLngPoint
import com.jaychoi.eattheland.core.network.CaptureOutcome
import com.jaychoi.eattheland.core.network.CellDto
import com.jaychoi.eattheland.core.network.DataSourceException
import com.jaychoi.eattheland.core.testing.FakeAuthDataSource
import com.jaychoi.eattheland.core.testing.FakeCellDataSource
import com.jaychoi.eattheland.core.testing.FakeHexGrid
import com.jaychoi.eattheland.core.testing.FakePendingCaptureDataSource
import com.jaychoi.eattheland.core.testing.FakePendingCaptureScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultTerritoryRepositoryTest {
    private val source = FakeCellDataSource()
    private val grid = FakeHexGrid()
    private val auth = FakeAuthDataSource(initialUid = "u1")
    private val pendingSource = FakePendingCaptureDataSource()
    private val scheduler = FakePendingCaptureScheduler()
    private var now = 1_000L
    private val clock = Clock { now }
    private val queue = PendingCaptureQueue(pendingSource, scheduler, clock)
    private val repo = DefaultTerritoryRepository(source, grid, auth, queue, clock)

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

    @Test
    fun `capture 는 내 uid·색·region·지금 시각(walkedAt)으로 데이터소스를 부르고 Captured`() = runTest {
        now = 123_456L
        assertEquals(CaptureResult.Captured, repo.capture(cell))
        val request = source.requests.single()
        assertEquals(cell.value, request.cellId)
        assertEquals(region.value, request.region)
        assertEquals("u1", request.uid)
        assertEquals(colorFor("u1"), request.color)
        assertEquals(123_456L, request.walkedAtMillis)
        assertEquals(0, scheduler.scheduled)
    }

    @Test
    fun `flushPending 은 큐에 넣은 시각을 walkedAt 으로 보낸다`() = runTest {
        now = 9_000_000L
        pendingSource.stored.value = listOf(PendingCapture(cell.value, 100L))
        assertEquals(0, repo.flushPending())
        assertEquals(listOf(100L), source.requests.map { it.walkedAtMillis })
    }

    @Test
    fun `이미 내 셀이면 AlreadyMine`() = runTest {
        source.captureOutcome = CaptureOutcome.AlreadyMine
        assertEquals(CaptureResult.AlreadyMine, repo.capture(cell))
    }

    @Test
    fun `오프라인이면 큐에 넣고 Queued, pendingCount 가 오른다`() = runTest {
        source.captureError = DataSourceException(DataSourceException.Kind.Offline)
        assertEquals(CaptureResult.Queued, repo.capture(cell))
        assertEquals(listOf(cell.value), pendingSource.stored.value.map { it.cellId })
        assertEquals(1, scheduler.scheduled)
        repo.pendingCount.test { assertEquals(1, awaitItem()) }
    }

    @Test
    fun `트랜잭션이 10초 안에 끝나지 않으면(오프라인 대기) 큐에 넣고 Queued`() = runTest {
        source.captureHangs = true
        assertEquals(CaptureResult.Queued, repo.capture(cell))
        assertEquals(listOf(cell.value), pendingSource.stored.value.map { it.cellId })
        assertEquals(CAPTURE_TIMEOUT_MS, currentTime)
    }

    @Test
    fun `캡처 도중 취소되면(산책 종료) 큐에 넣고 나서 취소를 전파한다`() = runTest {
        source.captureHangs = true
        val job = launch { repo.capture(cell) }
        runCurrent()
        job.cancel()
        runCurrent()
        assertEquals(listOf(cell.value), pendingSource.stored.value.map { it.cellId })
    }

    @Test
    fun `오프라인에서 이미 큐에 있는 셀을 다시 밟으면 AlreadyQueued`() = runTest {
        source.captureError = DataSourceException(DataSourceException.Kind.Offline)
        assertEquals(CaptureResult.Queued, repo.capture(cell))
        assertEquals(CaptureResult.AlreadyQueued, repo.capture(cell))
        assertEquals(1, pendingSource.stored.value.size)
    }

    @Test
    fun `flushPending 은 일시 오류(Unknown) 항목을 남기고 다음으로 간다`() = runTest {
        pendingSource.stored.value = listOf(
            PendingCapture(cell.value, 100L),
            PendingCapture(other.value, 200L),
        )
        source.captureErrorOnce = DataSourceException(DataSourceException.Kind.Unknown)
        assertEquals(1, repo.flushPending())
        assertEquals(listOf(cell.value, other.value), source.captures)
        assertEquals(listOf(cell.value), pendingSource.stored.value.map { it.cellId })
    }

    @Test
    fun `로그인 전이거나 권한 오류면 Failed 이고 큐에 넣지 않는다`() = runTest {
        auth.uid.value = null
        assertTrue(repo.capture(cell) is CaptureResult.Failed)
        auth.uid.value = "u1"
        source.captureError = DataSourceException(DataSourceException.Kind.PermissionDenied)
        assertTrue(repo.capture(cell) is CaptureResult.Failed)
        assertTrue(pendingSource.stored.value.isEmpty())
    }

    @Test
    fun `flushPending 은 오래된 순으로 보내고 성공한 것만 지운다`() = runTest {
        pendingSource.stored.value = listOf(
            PendingCapture(other.value, 200L),
            PendingCapture(cell.value, 100L),
        )
        assertEquals(0, repo.flushPending())
        assertEquals(listOf(cell.value, other.value), source.captures)
        assertTrue(pendingSource.stored.value.isEmpty())
    }

    @Test
    fun `flushPending 중 오프라인이면 멈추고 남은 개수를 돌려준다`() = runTest {
        pendingSource.stored.value = listOf(
            PendingCapture(cell.value, 100L),
            PendingCapture(other.value, 200L),
        )
        source.captureError = DataSourceException(DataSourceException.Kind.Offline)
        assertEquals(2, repo.flushPending())
        assertEquals(listOf(cell.value), source.captures) // 첫 실패에서 멈춘다
        assertEquals(2, pendingSource.stored.value.size)
    }

    @Test
    fun `flushPending 은 권한 오류 항목을 버리고 계속 간다`() = runTest {
        pendingSource.stored.value = listOf(PendingCapture(cell.value, 100L))
        source.captureError = DataSourceException(DataSourceException.Kind.PermissionDenied)
        assertEquals(0, repo.flushPending())
        assertTrue(pendingSource.stored.value.isEmpty())
    }

    private companion object {
        const val FIRST_RETRY_MS = 5_000L
        const val CAPTURE_TIMEOUT_MS = 10_000L
    }
}
