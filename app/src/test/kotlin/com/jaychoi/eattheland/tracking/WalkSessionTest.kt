package com.jaychoi.eattheland.tracking

import com.jaychoi.eattheland.core.common.Clock
import com.jaychoi.eattheland.core.model.WalkSummary
import com.jaychoi.eattheland.core.testing.FakeTrackingRepository
import com.jaychoi.eattheland.core.testing.FakeWalkRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkSessionTest {
    private val tracking = FakeTrackingRepository()
    private val walks = FakeWalkRepository()
    private var now = 0L
    private val session = WalkSession(tracking, walks, Clock { now })

    @Test
    fun `start 는 지금 시각으로 추적을 시작하고, finish 는 요약을 남기고 저장한다`() = runTest {
        now = 1_000L
        session.start()
        assertEquals(1_000L, tracking.state.value.startedAtMillis)
        tracking.onCaptured()
        tracking.onDistance(50.0)
        now = 61_000L
        session.finish()
        val expected = WalkSummary(1_000L, 61_000L, cells = 1, meters = 50.0)
        assertEquals(expected, tracking.state.value.lastSummary)
        assertEquals(listOf(expected), walks.saved)
    }

    @Test
    fun `저장 실패는 조용히 지나간다 - 요약은 남는다`() = runTest {
        walks.result = false
        session.start()
        session.finish()
        assertTrue(tracking.state.value.lastSummary != null)
    }

    @Test
    fun `시작 없이 finish 면 저장하지 않는다`() = runTest {
        session.finish()
        assertTrue(walks.saved.isEmpty())
    }
}
