package com.jaychoi.eattheland.core.data.sync

import app.cash.turbine.test
import com.jaychoi.eattheland.core.common.Clock
import com.jaychoi.eattheland.core.datastore.PendingCapture
import com.jaychoi.eattheland.core.model.CellId
import com.jaychoi.eattheland.core.testing.FakePendingCaptureDataSource
import com.jaychoi.eattheland.core.testing.FakePendingCaptureScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PendingCaptureQueueTest {
    private val source = FakePendingCaptureDataSource()
    private val scheduler = FakePendingCaptureScheduler()
    private var now = 100_000L
    private val queue = PendingCaptureQueue(source, scheduler, Clock { now })

    private fun cell(i: Int) = CellId("8b30e1d8c0${"%03x".format(i)}fff")

    @Test
    fun `enqueue 는 시각과 함께 저장하고 자동 전송을 예약한다`() = runTest {
        assertEquals(true, queue.enqueue(cell(1)))
        assertEquals(listOf(PendingCapture(cell(1).value, 100_000L)), source.stored.value)
        assertEquals(1, scheduler.scheduled)
        queue.count.test { assertEquals(1, awaitItem()) }
    }

    @Test
    fun `같은 셀을 다시 넣으면 시각만 갱신된다`() = runTest {
        queue.enqueue(cell(1))
        now = 200_000L
        assertEquals(false, queue.enqueue(cell(1)))
        assertEquals(listOf(PendingCapture(cell(1).value, 200_000L)), source.stored.value)
    }

    @Test
    fun `300칸을 넘으면 가장 오래된 것부터 버린다`() = runTest {
        repeat(300) { i ->
            now = 1_000L + i
            queue.enqueue(cell(i))
        }
        now = 9_000L
        queue.enqueue(cell(300))
        val stored = source.stored.value
        assertEquals(300, stored.size)
        assertEquals(cell(1).value, stored.first().cellId)
        assertEquals(cell(300).value, stored.last().cellId)
    }

    @Test
    fun `snapshot 은 24시간 지난 항목을 지우고 오래된 순으로 준다`() = runTest {
        val day = 24L * 60 * 60 * 1_000
        source.stored.value = listOf(
            PendingCapture(cell(2).value, 50_000L),
            PendingCapture(cell(1).value, 40_000L),
            PendingCapture(cell(0).value, 10L), // 만료
        )
        now = 10L + day + 1
        assertEquals(
            listOf(PendingCell(cell(1), 40_000L), PendingCell(cell(2), 50_000L)),
            queue.snapshot(),
        )
        assertEquals(2, source.stored.value.size)
    }

    @Test
    fun `remove 는 그 셀만 지운다`() = runTest {
        queue.enqueue(cell(1))
        queue.enqueue(cell(2))
        queue.remove(PendingCell(cell(1), now))
        assertEquals(listOf(cell(2).value), source.stored.value.map { it.cellId })
    }

    @Test
    fun `보낸 뒤 다시 들어온 같은 셀(더 새 시각)은 remove 가 지우지 않는다`() = runTest {
        queue.enqueue(cell(1))
        val sent = queue.snapshot().single()
        now = 500_000L
        queue.enqueue(cell(1)) // 재전송 중 실시간 캡처가 다시 넣음
        queue.remove(sent)
        assertEquals(listOf(PendingCapture(cell(1).value, 500_000L)), source.stored.value)
    }
}
