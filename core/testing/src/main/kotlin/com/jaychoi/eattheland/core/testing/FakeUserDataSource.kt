package com.jaychoi.eattheland.core.testing

import com.jaychoi.eattheland.core.network.UserDataSource
import com.jaychoi.eattheland.core.network.UserDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeUserDataSource : UserDataSource {
    val users = MutableStateFlow<Map<String, UserDto>>(emptyMap())

    override fun observe(uid: String): Flow<UserDto?> = users.map { it[uid] }
}
