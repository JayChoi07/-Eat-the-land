package com.jaychoi.eattheland.core.network

import kotlinx.coroutines.flow.Flow

interface CellDataSource {
    /** `cells where region in regions` 실시간 스냅샷. 문서 ID → DTO. regions 가 비면 빈 맵 한 번. */
    fun observe(regions: Set<String>): Flow<Map<String, CellDto>>
}
