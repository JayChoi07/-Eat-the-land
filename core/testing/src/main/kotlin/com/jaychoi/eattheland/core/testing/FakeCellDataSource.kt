package com.jaychoi.eattheland.core.testing

import com.jaychoi.eattheland.core.network.CellDataSource
import com.jaychoi.eattheland.core.network.CellDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

class FakeCellDataSource : CellDataSource {
    val docs = MutableStateFlow<Map<String, CellDto>>(emptyMap())
    val requested = mutableListOf<Set<String>>()

    /** 수집(리스너 등록) 횟수. 같은 Flow 를 다시 수집해도 늘어난다. */
    var subscriptions: Int = 0
        private set

    /** 설정돼 있으면 수집이 그 예외로 끝난다(Firestore 리스너 오류 흉내). 수집할 때마다 다시 읽는다. */
    var observeError: Throwable? = null

    /** true 면 오류 전에 현재 값을 한 번 낸다(잘 받다가 리스너가 끊기는 경우). */
    var emitBeforeError: Boolean = false

    override fun observe(regions: Set<String>): Flow<Map<String, CellDto>> {
        requested += regions
        return flow {
            subscriptions++
            val error = observeError
            if (error == null) {
                emitAll(docs)
            } else {
                if (emitBeforeError) emit(docs.value)
                throw error
            }
        }
    }
}
