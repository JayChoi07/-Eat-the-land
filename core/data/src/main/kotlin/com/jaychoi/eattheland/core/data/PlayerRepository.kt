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

    /**
     * 스펙 C §5. 프로필·닉네임 예약을 지우고 Auth 계정을 지운다.
     * Auth 삭제만 실패하면 로그아웃하고 성공. null = 성공.
     */
    suspend fun deleteAccount(): PlayerError?

    /** 스펙 C §9 셀 카드용. 소유자 닉네임 — 문서가 없으면(탈퇴) null. 세션 메모리 캐시. */
    suspend fun nicknameOf(uid: String): String?
}
