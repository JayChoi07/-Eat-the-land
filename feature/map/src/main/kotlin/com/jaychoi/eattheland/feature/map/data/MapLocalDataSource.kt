package com.jaychoi.eattheland.feature.map.data

import javax.inject.Inject

/**
 * 캐시 경계. Repository 테스트가 fake로 갈아끼울 수 있도록 인터페이스로 둔다 (R-30-10).
 * 구현이 하나뿐이어도 이 경계는 테스트 대역을 위해 필요하다 (R-00-05 예외).
 */
interface MapLocalDataSource {
    suspend fun save(dto: MapDto)

    suspend fun load(): MapDto?
}

/**
 * 메모리 캐시 스텁. Room DAO나 DataStore로 교체한다.
 * 스텁 본문에는 정지 지점이 없어 detekt RedundantSuspendModifier가 걸린다. 교체하면 실제로 suspend가
 * 되므로 시그니처를 유지하고 이 클래스에서만 억제한다.
 */
@Suppress("RedundantSuspendModifier")
class DefaultMapLocalDataSource @Inject constructor() : MapLocalDataSource {
    private var cache: MapDto? = null

    override suspend fun save(dto: MapDto) {
        cache = dto
    }

    override suspend fun load(): MapDto? = cache
}
