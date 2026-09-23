package com.jaychoi.eattheland.feature.map.data

import com.jaychoi.eattheland.feature.map.model.Map
import javax.inject.Inject

/** 네트워크 DTO. 도메인 모델과 분리하고, 매핑은 이 파일에서 한다. */
data class MapDto(val id: String, val name: String)

fun MapDto.toDomain() = Map(id = id, name = name)

/**
 * Repository 테스트가 fake로 갈아끼울 수 있도록 인터페이스로 둔다 (R-30-10).
 * 구현이 하나뿐이어도 이 경계는 테스트 대역을 위해 필요하다 (R-00-05 예외).
 */
interface MapRemoteDataSource {
    /** Retrofit 서비스 호출로 교체한다. 예외는 그대로 던지고 Repository가 잡는다. */
    suspend fun fetch(): MapDto
}

/** 스텁 본문에는 정지 지점이 없다. Retrofit으로 교체하면 실제 suspend가 되므로 시그니처를 유지한다. */
@Suppress("RedundantSuspendModifier")
class DefaultMapRemoteDataSource @Inject constructor() : MapRemoteDataSource {
    override suspend fun fetch(): MapDto = MapDto(id = "1", name = "remote")
}
