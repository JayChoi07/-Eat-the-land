package com.jaychoi.eattheland.core.data.walk

import com.jaychoi.eattheland.core.model.WalkSummary
import com.jaychoi.eattheland.core.network.DataSourceException
import com.jaychoi.eattheland.core.network.WalkDto
import com.jaychoi.eattheland.core.testing.FakeAuthDataSource
import com.jaychoi.eattheland.core.testing.FakeWalkDataSource
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultWalkRepositoryTest {
    private val walks = FakeWalkDataSource()
    private val auth = FakeAuthDataSource(initialUid = "u1")
    private val summary =
        WalkSummary(startedAtMillis = 1_000L, endedAtMillis = 61_000L, cells = 3, meters = 1_234.6)

    private fun repo(scheduler: TestCoroutineScheduler) =
        DefaultWalkRepository(walks, auth, StandardTestDispatcher(scheduler))

    @Test
    fun `내 uid 아래에 미터를 반올림한 문서를 만든다`() = runTest {
        assertTrue(repo(testScheduler).save(summary))
        assertEquals(
            listOf(
                "u1" to WalkDto(
                    startedAtMillis = 1_000L,
                    endedAtMillis = 61_000L,
                    cells = 3,
                    meters = 1_235,
                ),
            ),
            walks.created,
        )
    }

    @Test
    fun `로그인 전이면 저장하지 않고 false`() = runTest {
        auth.uid.value = null
        assertFalse(repo(testScheduler).save(summary))
        assertTrue(walks.created.isEmpty())
    }

    @Test
    fun `데이터소스 실패는 삼키고 false`() = runTest {
        walks.error = DataSourceException(DataSourceException.Kind.PermissionDenied)
        assertFalse(repo(testScheduler).save(summary))
    }

    @Test
    fun `응답이 없으면(오프라인 큐) 5초 뒤 포기하고 false`() = runTest {
        walks.hangs = true
        assertFalse(repo(testScheduler).save(summary))
    }
}
