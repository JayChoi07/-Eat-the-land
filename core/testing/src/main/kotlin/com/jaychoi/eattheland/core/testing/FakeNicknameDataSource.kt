package com.jaychoi.eattheland.core.testing

import com.jaychoi.eattheland.core.network.DataSourceException
import com.jaychoi.eattheland.core.network.NicknameDataSource

class FakeNicknameDataSource : NicknameDataSource {
    val calls = mutableListOf<Triple<String, String, Int>>()
    var error: DataSourceException? = null

    override suspend fun setNickname(uid: String, nickname: String, colorIfNew: Int) {
        calls += Triple(uid, nickname, colorIfNew)
        error?.let { throw it }
    }
}
