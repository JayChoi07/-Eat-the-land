package com.jaychoi.eattheland.core.testing

import com.jaychoi.eattheland.core.network.DataSourceException
import com.jaychoi.eattheland.core.network.UserDataSource
import com.jaychoi.eattheland.core.network.UserDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class FakeUserDataSource : UserDataSource {
    val users = MutableStateFlow<Map<String, UserDto>>(emptyMap())

    /** 설정돼 있으면 수집이 그 예외로 끝난다(Firestore 리스너 오류 흉내). 수집할 때마다 다시 읽는다. */
    var observeError: Throwable? = null

    /** true 면 오류 전에 현재 값을 한 번 낸다(잘 받다가 리스너가 끊기는 경우). */
    var emitBeforeError: Boolean = false

    override fun observe(uid: String): Flow<UserDto?> = flow {
        val error = observeError
        if (error == null) {
            emitAll(users.map { it[uid] })
        } else {
            if (emitBeforeError) emit(users.value[uid])
            throw error
        }
    }

    var topError: DataSourceException? = null
    var topCalls = 0
        private set
    var countCalls = 0
        private set

    override suspend fun topByCellCount(limit: Int): List<Pair<String, UserDto>> {
        topCalls++
        topError?.let { throw it }
        return users.value.entries
            .sortedByDescending { it.value.cellCount ?: 0L }
            .take(limit)
            .map { it.key to it.value }
    }

    override suspend fun countWithMoreCells(than: Int): Int {
        countCalls++
        return users.value.values.count { (it.cellCount ?: 0L) > than }
    }
}
