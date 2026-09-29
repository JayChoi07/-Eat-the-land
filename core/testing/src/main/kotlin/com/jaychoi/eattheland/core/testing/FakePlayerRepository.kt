package com.jaychoi.eattheland.core.testing

import com.jaychoi.eattheland.core.data.PlayerRepository
import com.jaychoi.eattheland.core.model.Player
import com.jaychoi.eattheland.core.model.PlayerError
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
}
