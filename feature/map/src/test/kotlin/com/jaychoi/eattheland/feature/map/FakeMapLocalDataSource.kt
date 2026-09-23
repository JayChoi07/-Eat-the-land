package com.jaychoi.eattheland.feature.map

import com.jaychoi.eattheland.feature.map.data.MapDto
import com.jaychoi.eattheland.feature.map.data.MapLocalDataSource

/**
 * 캐시 fake (R-30-02, R-30-10). `cache = null` 이면 캐시 없음,
 * `failOnLoad = true` 면 캐시 조회 자체가 실패한다(네트워크·캐시 연속 실패 경로 검증용).
 */
class FakeMapLocalDataSource : MapLocalDataSource {
    var cache: MapDto? = null
    var failOnLoad: Boolean = false

    override suspend fun save(dto: MapDto) {
        cache = dto
    }

    override suspend fun load(): MapDto? {
        if (failOnLoad) error("캐시 조회 실패")
        return cache
    }
}
