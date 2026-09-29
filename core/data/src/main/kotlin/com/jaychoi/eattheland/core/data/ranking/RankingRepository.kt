package com.jaychoi.eattheland.core.data.ranking

import com.jaychoi.eattheland.core.model.RankingLoad

interface RankingRepository {
    /** 스펙 C §6. force=false 면 세션 캐시를 먼저 쓴다. 실패는 [RankingLoad.Failure](캐시 동봉). */
    suspend fun load(force: Boolean = false): RankingLoad
}
