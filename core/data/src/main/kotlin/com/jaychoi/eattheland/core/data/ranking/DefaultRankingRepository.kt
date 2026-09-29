package com.jaychoi.eattheland.core.data.ranking

import com.jaychoi.eattheland.core.model.MyRank
import com.jaychoi.eattheland.core.model.RankEntry
import com.jaychoi.eattheland.core.model.Ranking
import com.jaychoi.eattheland.core.model.RankingError
import com.jaychoi.eattheland.core.model.RankingLoad
import com.jaychoi.eattheland.core.network.AuthDataSource
import com.jaychoi.eattheland.core.network.DataSourceException
import com.jaychoi.eattheland.core.network.UserDataSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 상위 [TOP_LIMIT] 일회성 읽기 + 내 순위(목록 안이면 0 읽기, 밖이면 count 1) — 리소스 최소(사용자 결정 3).
 * 세션 메모리 캐시라 @Singleton. 동시 load 는 Mutex 로 한 번만.
 */
@Singleton
class DefaultRankingRepository @Inject constructor(
    private val users: UserDataSource,
    private val auth: AuthDataSource,
) : RankingRepository {
    private var cache: Ranking? = null
    private val mutex = Mutex()

    override suspend fun load(force: Boolean): RankingLoad = mutex.withLock {
        cache?.takeIf { !force }?.let { return@withLock RankingLoad.Success(it) }
        try {
            val ranking = fetch()
            cache = ranking
            RankingLoad.Success(ranking)
        } catch (e: CancellationException) {
            throw e
        } catch (e: DataSourceException) {
            RankingLoad.Failure(e.toRankingError(), cache)
        }
    }

    private suspend fun fetch(): Ranking {
        val rows = users.topByCellCount(TOP_LIMIT)
        val entries = rows.map { (uid, dto) ->
            val cells = dto.cellCount?.toInt() ?: 0
            // 동점은 같은 순위: 칸 수가 더 많은 사람 수 + 1 (1,1,3)
            val rank = rows.count { (it.second.cellCount?.toInt() ?: 0) > cells } + 1
            RankEntry(rank, uid, dto.nickname.orEmpty(), (dto.color ?: 0L).toInt(), cells)
        }
        val uid = auth.uid.first() ?: return Ranking(entries, me = null)
        return Ranking(entries, me = myRank(uid, entries))
    }

    private suspend fun myRank(uid: String, entries: List<RankEntry>): MyRank? {
        val inList = entries.firstOrNull { it.uid == uid }
        val mine = inList?.cellCount ?: users.observe(uid).first()?.cellCount?.toInt() ?: 0
        return when {
            mine == 0 -> null
            inList != null -> MyRank(inList.rank, mine)
            else -> MyRank(users.countWithMoreCells(mine) + 1, mine)
        }
    }

    private fun DataSourceException.toRankingError(): RankingError = when (kind) {
        DataSourceException.Kind.Offline -> RankingError.Offline
        else -> RankingError.Unknown
    }

    private companion object {
        const val TOP_LIMIT = 50
    }
}
