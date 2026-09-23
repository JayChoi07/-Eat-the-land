package com.jaychoi.eattheland.core.data

import app.cash.turbine.test
import com.jaychoi.eattheland.core.model.Player
import com.jaychoi.eattheland.core.model.PlayerError
import com.jaychoi.eattheland.core.network.DataSourceException
import com.jaychoi.eattheland.core.network.UserDto
import com.jaychoi.eattheland.core.testing.FakeAuthDataSource
import com.jaychoi.eattheland.core.testing.FakeNicknameDataSource
import com.jaychoi.eattheland.core.testing.FakeUserDataSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultPlayerRepositoryTest {
    private val auth = FakeAuthDataSource(initialUid = "u1")
    private val users = FakeUserDataSource()
    private val nicknames = FakeNicknameDataSource()

    private fun repo(dispatcher: CoroutineDispatcher) = DefaultPlayerRepository(
        auth,
        users,
        nicknames,
        dispatcher,
    )

    @Test
    fun `로그인 전에는 null, 로그인 후 문서 없으면 null, 문서 생기면 Player`() = runTest {
        auth.uid.value = null
        val repo = repo(StandardTestDispatcher(testScheduler))
        repo.currentPlayer.test {
            assertNull(awaitItem())
            auth.uid.value = "u1"
            assertNull(awaitItem())
            users.users.value = mapOf("u1" to UserDto(nickname = "땅주인", color = 3, cellCount = 12))
            assertEquals(
                Player(uid = "u1", nickname = "땅주인", color = 3, cellCount = 12),
                awaitItem(),
            )
        }
    }

    @Test
    fun `ensureSignedIn 실패는 Network 에러`() = runTest {
        auth.uid.value = null
        auth.failSignIn = true
        assertEquals(
            PlayerError.Network,
            repo(StandardTestDispatcher(testScheduler)).ensureSignedIn(),
        )
    }

    @Test
    fun `setNickname 은 현재 uid 와 uid 기반 색 0~6 으로 데이터소스를 부른다`() = runTest {
        val repo = repo(StandardTestDispatcher(testScheduler))
        assertNull(repo.setNickname("땅주인"))
        val (uid, nickname, color) = nicknames.calls.single()
        assertEquals("u1", uid)
        assertEquals("땅주인", nickname)
        assertTrue(color in 0..6)
    }

    @Test
    fun `로그인 전 setNickname 은 Network 에러`() = runTest {
        auth.uid.value = null
        auth.failSignIn = true
        assertEquals(
            PlayerError.Network,
            repo(StandardTestDispatcher(testScheduler)).setNickname("땅주인"),
        )
        assertTrue(nicknames.calls.isEmpty())
    }

    @Test
    fun `NicknameTaken 은 NicknameTaken, Offline 은 Network, 그 밖은 Unknown`() = runTest {
        val repo = repo(StandardTestDispatcher(testScheduler))
        nicknames.error = DataSourceException(DataSourceException.Kind.NicknameTaken)
        assertEquals(PlayerError.NicknameTaken, repo.setNickname("x1"))
        nicknames.error = DataSourceException(DataSourceException.Kind.Offline)
        assertEquals(PlayerError.Network, repo.setNickname("x1"))
        nicknames.error = DataSourceException(DataSourceException.Kind.Unknown)
        assertTrue(repo.setNickname("x1") is PlayerError.Unknown)
    }

    @Test
    fun `users 스트림 오류는 예외 대신 null(프로필 없음)로 흘린다`() = runTest {
        users.observeError = DataSourceException(DataSourceException.Kind.PermissionDenied)
        val repo = repo(StandardTestDispatcher(testScheduler))
        repo.currentPlayer.test {
            assertNull(awaitItem())
            expectNoEvents() // auth.uid 는 StateFlow 라 완료되지 않는다 — 예외가 안 오는 것만 본다
            cancelAndIgnoreRemainingEvents()
        }
    }
}
