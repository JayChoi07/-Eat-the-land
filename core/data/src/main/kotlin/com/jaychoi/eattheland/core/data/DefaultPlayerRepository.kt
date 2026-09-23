package com.jaychoi.eattheland.core.data

import com.jaychoi.eattheland.core.common.IoDispatcher
import com.jaychoi.eattheland.core.model.Player
import com.jaychoi.eattheland.core.model.PlayerError
import com.jaychoi.eattheland.core.network.AuthDataSource
import com.jaychoi.eattheland.core.network.DataSourceException
import com.jaychoi.eattheland.core.network.NicknameDataSource
import com.jaychoi.eattheland.core.network.UserDataSource
import com.jaychoi.eattheland.core.network.UserDto
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class DefaultPlayerRepository @Inject constructor(
    private val auth: AuthDataSource,
    private val users: UserDataSource,
    private val nicknames: NicknameDataSource,
    @IoDispatcher private val io: CoroutineDispatcher,
) : PlayerRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override val currentPlayer: Flow<Player?> = auth.uid.flatMapLatest(::playerFor)

    private fun playerFor(uid: String?): Flow<Player?> =
        if (uid == null) flowOf(null) else users.observe(uid).map { it?.toPlayer(uid) }

    override suspend fun ensureSignedIn(): PlayerError? = withContext(io) {
        guard { auth.ensureSignedIn() }
    }

    override suspend fun setNickname(nickname: String): PlayerError? = withContext(io) {
        guard {
            val uid = auth.ensureSignedIn()
            nicknames.setNickname(uid = uid, nickname = nickname, colorIfNew = colorFor(uid))
        }
    }

    /** 스펙 §4: 서버 카운터가 없으므로 uid 해시로 0..6 배정. */
    private fun colorFor(uid: String): Int = uid.hashCode().mod(COLOR_COUNT)

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

    private companion object {
        const val COLOR_COUNT = 7
    }
}
