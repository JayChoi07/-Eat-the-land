package com.jaychoi.eattheland.core.data

import com.jaychoi.eattheland.core.model.Player
import com.jaychoi.eattheland.core.model.PlayerError
import kotlinx.coroutines.flow.Flow

interface PlayerRepository {
    /** 로그인 전·프로필 없음 → null. 온보딩 완료 판정은 "null 이 아닌가"다 (스펙 §5). */
    val currentPlayer: Flow<Player?>

    /** 익명 로그인 보장. 성공 null, 실패 에러. */
    suspend fun ensureSignedIn(): PlayerError?

    suspend fun setNickname(nickname: String): PlayerError?
}
