package com.jaychoi.eattheland.feature.map

import com.jaychoi.eattheland.feature.map.data.MapRepository
import com.jaychoi.eattheland.feature.map.model.Map
import com.jaychoi.eattheland.feature.map.model.MapResult

/** fake 우선, mock은 외부 경계만 (R-30-02). */
class FakeMapRepository : MapRepository {
    var result: MapResult = MapResult.Success(Map(id = "1", name = "fake"))

    var callCount: Int = 0
        private set

    override suspend fun getMap(): MapResult {
        callCount++
        return result
    }
}
