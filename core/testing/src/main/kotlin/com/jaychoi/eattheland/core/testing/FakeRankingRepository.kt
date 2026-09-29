package com.jaychoi.eattheland.core.testing

import com.jaychoi.eattheland.core.data.ranking.RankingRepository
import com.jaychoi.eattheland.core.model.Ranking
import com.jaychoi.eattheland.core.model.RankingLoad

class FakeRankingRepository : RankingRepository {
    var result: RankingLoad = RankingLoad.Success(Ranking(emptyList(), me = null))
    val loadCalls = mutableListOf<Boolean>()

    override suspend fun load(force: Boolean): RankingLoad {
        loadCalls += force
        return result
    }
}
