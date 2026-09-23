package com.jaychoi.eattheland.feature.map.domain

import com.jaychoi.eattheland.feature.map.data.MapRepository
import com.jaychoi.eattheland.feature.map.model.MapResult
import javax.inject.Inject

/**
 * 뼈대 템플릿이다. 지금처럼 Repository로 단순 위임만 한다면 UseCase를 만들지 말고
 * ViewModel이 Repository를 직접 호출한다 (R-16-02).
 * 승격 조건은 ViewModel 2개 이상이 공유하거나 Repository 2개 이상을 조합할 때다 (R-16-07).
 */
class GetMapUseCase @Inject constructor(
    private val repository: MapRepository,
) {
    /** public 함수는 invoke 하나 (R-16-01). */
    suspend operator fun invoke(): MapResult = repository.getMap()
}
