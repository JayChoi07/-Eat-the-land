package com.jaychoi.eattheland.core.data.walk

import com.jaychoi.eattheland.core.common.IoDispatcher
import com.jaychoi.eattheland.core.model.WalkSummary
import com.jaychoi.eattheland.core.network.AuthDataSource
import com.jaychoi.eattheland.core.network.DataSourceException
import com.jaychoi.eattheland.core.network.WalkDataSource
import com.jaychoi.eattheland.core.network.WalkDto
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 오프라인이면 Firestore 가 쓰기를 보관하고 응답하지 않는다 — 여기서는 [SAVE_TIMEOUT_MS] 뒤 포기한다.
 * 보관된 쓰기가 나중에 서버에 닿아도 같은 문서가 한 번 더 생기는 게 아니라 그 한 건이 늦게 도착하는 것이라 무해하다.
 */
class DefaultWalkRepository @Inject constructor(
    private val walks: WalkDataSource,
    private val auth: AuthDataSource,
    @IoDispatcher private val io: CoroutineDispatcher,
) : WalkRepository {
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override suspend fun save(summary: WalkSummary): Boolean = withContext(io) {
        val uid = auth.uid.first() ?: return@withContext false
        val dto = WalkDto(
            startedAtMillis = summary.startedAtMillis,
            endedAtMillis = summary.endedAtMillis,
            cells = summary.cells,
            meters = summary.meters.roundToInt(),
        )
        try {
            withTimeoutOrNull(SAVE_TIMEOUT_MS) { walks.create(uid, dto) } != null
        } catch (e: CancellationException) {
            throw e
        } catch (e: DataSourceException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    private companion object {
        const val SAVE_TIMEOUT_MS = 5_000L
    }
}
