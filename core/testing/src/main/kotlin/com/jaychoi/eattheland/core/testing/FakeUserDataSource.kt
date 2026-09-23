package com.jaychoi.eattheland.core.testing

import com.jaychoi.eattheland.core.network.UserDataSource
import com.jaychoi.eattheland.core.network.UserDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class FakeUserDataSource : UserDataSource {
    val users = MutableStateFlow<Map<String, UserDto>>(emptyMap())

    /** 설정하면 observe 가 그 예외로 끝나는 스트림을 돌려준다(Firestore 리스너 오류 흉내). */
    var observeError: Throwable? = null

    override fun observe(uid: String): Flow<UserDto?> {
        val error = observeError
        return if (error != null) flow { throw error } else users.map { it[uid] }
    }
}
