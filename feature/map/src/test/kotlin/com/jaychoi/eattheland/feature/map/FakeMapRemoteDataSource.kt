package com.jaychoi.eattheland.feature.map

import com.jaychoi.eattheland.feature.map.data.MapDto
import com.jaychoi.eattheland.feature.map.data.MapRemoteDataSource
import java.io.IOException

/**
 * fake 우선, mock은 외부 경계만 (R-30-02). `dto = null` 이면 네트워크 실패를 흉내 낸다.
 * Repository 테스트는 이 fake를 주입해 조립한다 (R-30-10).
 */
class FakeMapRemoteDataSource : MapRemoteDataSource {
    var dto: MapDto? = MapDto(id = "1", name = "remote")

    override suspend fun fetch(): MapDto = dto ?: throw IOException("네트워크 실패")
}
