package com.jaychoi.eattheland.core.testing

import com.jaychoi.eattheland.core.network.DataSourceException
import com.jaychoi.eattheland.core.network.WalkDataSource
import com.jaychoi.eattheland.core.network.WalkDto
import kotlinx.coroutines.awaitCancellation

class FakeWalkDataSource : WalkDataSource {
    val created = mutableListOf<Pair<String, WalkDto>>()
    var error: DataSourceException? = null

    /** true 면 응답하지 않는다(오프라인에서 보관된 쓰기 흉내). */
    var hangs = false

    override suspend fun create(uid: String, walk: WalkDto) {
        created += uid to walk
        error?.let { throw it }
        if (hangs) awaitCancellation()
    }
}
