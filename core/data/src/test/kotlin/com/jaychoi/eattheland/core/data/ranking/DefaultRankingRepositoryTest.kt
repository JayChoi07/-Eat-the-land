package com.jaychoi.eattheland.core.data.ranking

import com.jaychoi.eattheland.core.model.MyRank
import com.jaychoi.eattheland.core.model.RankingError
import com.jaychoi.eattheland.core.model.RankingLoad
import com.jaychoi.eattheland.core.network.DataSourceException
import com.jaychoi.eattheland.core.network.UserDto
import com.jaychoi.eattheland.core.testing.FakeAuthDataSource
import com.jaychoi.eattheland.core.testing.FakeUserDataSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultRankingRepositoryTest {
    private val users = FakeUserDataSource()
    private val auth = FakeAuthDataSource(initialUid = "me")
    private val repo = DefaultRankingRepository(users, auth)

    private fun user(nickname: String, cells: Int, color: Int = 1) = UserDto(
        nickname = nickname,
        nicknameLower = nickname.lowercase(),
        color = color.toLong(),
        cellCount = cells.toLong(),
    )

    private suspend fun load(force: Boolean = false) = repo.load(force) as RankingLoad.Success

    @Test
    fun `칸 수 내림차순 목록과 목록 안의 내 순위`() = runTest {
        users.users.value = mapOf("a" to user("A", 10), "me" to user("나", 7), "b" to user("B", 3))
        val ranking = load().ranking
        assertEquals(listOf("A", "나", "B"), ranking.entries.map { it.nickname })
        assertEquals(listOf(1, 2, 3), ranking.entries.map { it.rank })
        assertEquals(MyRank(rank = 2, cellCount = 7), ranking.me)
        assertEquals(0, users.countCalls)
    }

    @Test
    fun `동점은 같은 순위이고 다음 순위는 건너뛴다`() = runTest {
        users.users.value = mapOf("a" to user("A", 10), "b" to user("B", 10), "me" to user("나", 4))
        val ranking = load().ranking
        assertEquals(listOf(1, 1, 3), ranking.entries.map { it.rank })
        assertEquals(MyRank(3, 4), ranking.me)
    }

    @Test
    fun `목록 밖이면 count 집계로 내 순위를 구한다`() = runTest {
        users.users.value =
            (1..60).associate { "u$it" to user("U$it", 100 - it) } + ("me" to user("나", 5))
        val ranking = load().ranking
        assertEquals(50, ranking.entries.size)
        assertEquals(MyRank(rank = 61, cellCount = 5), ranking.me)
        assertEquals(1, users.countCalls)
    }

    @Test
    fun `0칸이면 내 순위는 없다 - 목록에 있어도`() = runTest {
        users.users.value = mapOf("a" to user("A", 3), "me" to user("나", 0))
        assertNull(load().ranking.me)
        assertEquals(0, users.countCalls)
    }

    @Test
    fun `두 번째 load 는 캐시를 쓰고 force 면 다시 읽는다`() = runTest {
        users.users.value = mapOf("me" to user("나", 1))
        load()
        users.users.value = mapOf("me" to user("나", 9))
        assertEquals(1, load().ranking.me?.cellCount)
        assertEquals(9, load(force = true).ranking.me?.cellCount)
        assertEquals(2, users.topCalls)
    }

    @Test
    fun `실패는 에러와 캐시를 함께 준다`() = runTest {
        users.users.value = mapOf("me" to user("나", 1))
        load()
        users.topError = DataSourceException(DataSourceException.Kind.Offline)
        val failure = repo.load(force = true) as RankingLoad.Failure
        assertEquals(RankingError.Offline, failure.error)
        assertEquals(1, failure.cached?.me?.cellCount)
    }

    @Test
    fun `첫 로드가 실패하면 캐시 없이 실패`() = runTest {
        users.topError = DataSourceException(DataSourceException.Kind.Unknown)
        val failure = repo.load(force = false) as RankingLoad.Failure
        assertEquals(RankingError.Unknown, failure.error)
        assertNull(failure.cached)
    }

    @Test
    fun `uid 가 바뀌면(계정 삭제 뒤 재가입) 캐시를 버리고 새 계정 기준으로 읽는다`() = runTest {
        users.users.value = mapOf("me" to user("나", 5), "new" to user("새계정", 0))
        assertEquals(MyRank(1, 5), load().ranking.me)
        auth.uid.value = "new"
        val ranking = load().ranking
        assertNull(ranking.me)
        assertEquals(2, users.topCalls)
    }

    @Test
    fun `uid 가 바뀐 뒤 실패하면 옛 계정 캐시를 동봉하지 않는다`() = runTest {
        users.users.value = mapOf("me" to user("나", 5))
        load()
        auth.uid.value = "new"
        users.topError = DataSourceException(DataSourceException.Kind.Offline)
        val failure = repo.load(force = false) as RankingLoad.Failure
        assertNull(failure.cached)
    }

    @Test
    fun `로그인 전이면 목록만 있고 내 순위는 없다`() = runTest {
        auth.uid.value = null
        users.users.value = mapOf("a" to user("A", 3))
        val ranking = load().ranking
        assertTrue(ranking.entries.isNotEmpty())
        assertNull(ranking.me)
    }
}
