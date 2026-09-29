package com.jaychoi.eattheland.core.model

/** 랭킹 한 줄. rank 는 동점이면 같고 다음 순위는 건너뛴다(1,1,3). */
data class RankEntry(
    val rank: Int,
    val uid: String,
    val nickname: String,
    val color: Int,
    val cellCount: Int,
)

/** 내 순위. 0칸이면 null("아직 순위가 없어요"). */
data class MyRank(val rank: Int, val cellCount: Int)

data class Ranking(val entries: List<RankEntry>, val me: MyRank?)

enum class RankingError { Offline, Unknown }

sealed interface RankingLoad {
    data class Success(val ranking: Ranking) : RankingLoad

    /** 실패해도 세션 캐시가 있으면 함께 준다 — 화면은 목록을 유지하고 배너만 띄운다(스펙 C §6). */
    data class Failure(val error: RankingError, val cached: Ranking?) : RankingLoad
}
