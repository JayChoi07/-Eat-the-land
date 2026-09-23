package com.jaychoi.eattheland.feature.map.data

import com.jaychoi.eattheland.core.common.IoDispatcher
import com.jaychoi.eattheland.feature.map.model.MapError
import com.jaychoi.eattheland.feature.map.model.MapResult
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * 예외는 데이터 계층 경계에서 전부 잡아 도메인 에러 타입으로 바꾼다. 위로 예외를 던지지 않는다.
 * data는 ui·domain을 참조하지 않는다. 계층 타입 중에는 model만 import한다 (R-11-01).
 */
class DefaultMapRepository @Inject constructor(
    private val remote: MapRemoteDataSource,
    private val local: MapLocalDataSource,
    @IoDispatcher private val io: CoroutineDispatcher,
) : MapRepository {

    // R-23: 데이터 계층 경계는 모든 예외를 잡아 도메인 에러로 바꿔야 한다. 그 변환이 이 함수의 일이므로
    // 광범위 catch 와 예외를 값으로 흘리는 것(SwallowedException)이 여기서는 규칙 위반이 아니다.
    // 전역 비활성화 대신 이 한 지점만 억제한다.
    //
    // 바깥 try 가 변환 경계다. 캐시 폴백(local.load())은 그 안쪽에 있어야 폴백이 던진 예외도
    // 도메인 에러로 바뀐다. 폴백을 바깥 catch 절 안에 두면 그 예외는 아무도 잡지 못하고 위로 샌다.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override suspend fun getMap(): MapResult = withContext(io) {
        try {
            try {
                val dto = remote.fetch()
                local.save(dto)
                MapResult.Success(dto.toDomain())
            } catch (e: IOException) {
                // 네트워크 실패 시 로컬 캐시로 폴백하고, 캐시가 비어 있으면 Network 에러.
                // 캐시 조회 자체가 실패하면 바깥 catch 가 Unknown 으로 바꾼다.
                local.load()?.let { MapResult.Success(it.toDomain()) }
                    ?: MapResult.Failure(MapError.Network)
            }
        } catch (e: CancellationException) {
            throw e // 코루틴 취소는 에러가 아니므로 그대로 전파한다.
        } catch (e: Exception) {
            // HTTP 404 같은 응답 코드를 구분해야 하면 여기서 MapError.NotFound로 매핑한다.
            MapResult.Failure(MapError.Unknown(e))
        }
    }
}
