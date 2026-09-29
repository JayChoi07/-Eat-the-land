package com.jaychoi.eattheland.core.testing

import com.jaychoi.eattheland.core.data.walk.WalkRepository
import com.jaychoi.eattheland.core.model.WalkSummary

class FakeWalkRepository : WalkRepository {
    val saved = mutableListOf<WalkSummary>()
    var result = true

    override suspend fun save(summary: WalkSummary): Boolean {
        saved += summary
        return result
    }
}
