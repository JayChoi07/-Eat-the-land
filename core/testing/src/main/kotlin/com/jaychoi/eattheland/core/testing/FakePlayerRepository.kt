package com.jaychoi.eattheland.core.testing

import com.jaychoi.eattheland.core.data.PlayerRepository
import com.jaychoi.eattheland.core.model.Player
import com.jaychoi.eattheland.core.model.PlayerError
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakePlayerRepository : PlayerRepository {
    val playerFlow = MutableStateFlow<Player?>(null)
    var signInError: PlayerError? = null
    var setNicknameError: PlayerError? = null
    val setNicknameCalls = mutableListOf<String>()

    override val currentPlayer: Flow<Player?> = playerFlow

    override suspend fun ensureSignedIn(): PlayerError? = signInError

    override suspend fun setNickname(nickname: String): PlayerError? {
        setNicknameCalls += nickname
        if (setNicknameError == null) {
            playerFlow.value =
                Player(uid = "uid-fake", nickname = nickname, color = 0, cellCount = 0)
        }
        return setNicknameError
    }

    var deleteAccountError: PlayerError? = null
    var deleteCalls = 0
        private set

    override suspend fun deleteAccount(): PlayerError? {
        deleteCalls++
        if (deleteAccountError == null) playerFlow.value = null
        return deleteAccountError
    }

    /** uid → 닉네임. 없는 uid 는 null(떠난 사람). */
    val nicknames = mutableMapOf<String, String?>()
    val nicknameOfCalls = mutableListOf<String>()

    /** 0 보다 크면 응답 전에 그만큼 기다린다(느린 서버 흉내 — 테스트 스케줄러의 가상 시간). */
    var nicknameDelayMs = 0L

    override suspend fun nicknameOf(uid: String): String? {
        nicknameOfCalls += uid
        if (nicknameDelayMs > 0) delay(nicknameDelayMs)
        return nicknames[uid]
    }
}
