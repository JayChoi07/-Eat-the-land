package com.jaychoi.eattheland.core.data

import com.jaychoi.eattheland.core.common.IoDispatcher
import com.jaychoi.eattheland.core.data.sync.PendingCaptureQueue
import com.jaychoi.eattheland.core.model.Player
import com.jaychoi.eattheland.core.model.PlayerError
import com.jaychoi.eattheland.core.network.AuthDataSource
import com.jaychoi.eattheland.core.network.DataSourceException
import com.jaychoi.eattheland.core.network.NicknameDataSource
import com.jaychoi.eattheland.core.network.UserDataSource
import com.jaychoi.eattheland.core.network.UserDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 닉네임 캐시(nicknameOf)가 프로세스 안에서 하나여야 하므로 싱글턴. */
@Singleton
class DefaultPlayerRepository @Inject constructor(
    private val auth: AuthDataSource,
    private val users: UserDataSource,
    private val nicknames: NicknameDataSource,
    private val pendingQueue: PendingCaptureQueue,
    @IoDispatcher private val io: CoroutineDispatcher,
) : PlayerRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override val currentPlayer: Flow<Player?> = auth.uid.flatMapLatest(::playerFor)

    // 리스너 오류는 앱 루트를 죽이지 않고 다시 구독한다. 첫 값 전이면 "프로필 없음"으로 시작한다.
    private fun playerFor(uid: String?): Flow<Player?> =
        if (uid == null) {
            flowOf(null)
        } else {
            users.observe(uid).map { it?.toPlayer(uid) }.retryOnListenerError(fallback = null)
        }

    override suspend fun ensureSignedIn(): PlayerError? = withContext(io) {
        guard { auth.ensureSignedIn() }
    }

    override suspend fun setNickname(nickname: String): PlayerError? = withContext(io) {
        guard {
            val uid = auth.ensureSignedIn()
            nicknames.setNickname(uid = uid, nickname = nickname, colorIfNew = colorFor(uid))
        }
    }

    override suspend fun deleteAccount(): PlayerError? = withContext(io) {
        val uid = auth.uid.first() ?: return@withContext null
        // 타임아웃을 두지 않는다 — await 를 끊어도 SDK 의 삭제는 계속돼 "실패했다"고 알린 뒤 지워질 수 있다.
        // 오프라인 판정은 데이터소스가 서버 읽기(Source.SERVER)로 먼저 한다.
        val failure = guard { nicknames.deleteProfile(uid) }
        if (failure != null) return@withContext failure
        // 데이터는 지워졌다. 옛 계정의 미전송 캡처가 새 계정에 붙지 않게 큐를 비운다.
        pendingQueue.clear()
        // Auth 삭제가 재인증 요구 등으로 실패하면 로그아웃으로 같은 결과(새 익명 계정)를 만든다.
        if (guard { auth.deleteCurrentUser() } != null) auth.signOut()
        null
    }

    /** uid → 닉네임(null = 문서 없음). 오류는 캐시하지 않는다. */
    private val nicknameCache = mutableMapOf<String, String?>()
    private val nicknameMutex = Mutex()

    override suspend fun nicknameOf(uid: String): String? = nicknameMutex.withLock {
        if (uid in nicknameCache) return@withLock nicknameCache[uid]
        val loaded = runCatching { withContext(io) { users.get(uid)?.nickname } }
        val failure = loaded.exceptionOrNull()
        when (failure) {
            null -> loaded.getOrNull().also { nicknameCache[uid] = it }
            is CancellationException -> throw failure
            else -> null
        }
    }

    // R-23: 데이터 계층 경계에서 모든 실패를 도메인 에러로 바꾼다. 그 변환이 이 함수의 일이다.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private inline fun guard(block: () -> Unit): PlayerError? = try {
        block()
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: DataSourceException) {
        when (e.kind) {
            DataSourceException.Kind.NicknameTaken -> PlayerError.NicknameTaken

            DataSourceException.Kind.Offline -> PlayerError.Network

            DataSourceException.Kind.PermissionDenied,
            DataSourceException.Kind.Unknown,
            -> PlayerError.Unknown(e)
        }
    } catch (e: Exception) {
        PlayerError.Unknown(e)
    }

    private fun UserDto.toPlayer(uid: String): Player? {
        val name = nickname ?: return null
        return Player(
            uid = uid,
            nickname = name,
            color = (color ?: 0L).toInt(),
            cellCount = (cellCount ?: 0L).toInt(),
        )
    }
}
