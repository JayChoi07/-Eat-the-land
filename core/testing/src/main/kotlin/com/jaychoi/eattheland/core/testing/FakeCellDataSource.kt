package com.jaychoi.eattheland.core.testing

import com.jaychoi.eattheland.core.network.CellDataSource
import com.jaychoi.eattheland.core.network.CellDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

class FakeCellDataSource : CellDataSource {
    val docs = MutableStateFlow<Map<String, CellDto>>(emptyMap())
    val requested = mutableListOf<Set<String>>()

    /** 설정하면 observe 가 그 예외로 끝나는 스트림을 돌려준다(Firestore 리스너 오류 흉내). */
    var observeError: Throwable? = null

    override fun observe(regions: Set<String>): Flow<Map<String, CellDto>> {
        requested += regions
        val error = observeError
        return if (error != null) flow { throw error } else docs
    }
}
