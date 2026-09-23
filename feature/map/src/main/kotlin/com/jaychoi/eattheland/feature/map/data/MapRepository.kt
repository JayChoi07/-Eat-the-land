package com.jaychoi.eattheland.feature.map.data

import com.jaychoi.eattheland.feature.map.model.MapResult

/** Repository는 인터페이스와 구현 모두 data 계층에 둔다. 다른 계층은 이 인터페이스에만 의존한다 (R-11-02). */
interface MapRepository {
    suspend fun getMap(): MapResult
}
