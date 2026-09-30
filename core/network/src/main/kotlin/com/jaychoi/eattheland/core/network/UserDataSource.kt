package com.jaychoi.eattheland.core.network

import androidx.annotation.Keep
import kotlinx.coroutines.flow.Flow

/**
 * Firestore `users/{uid}` 문서. 필드는 전부 nullable — 서버 스키마 변경에 파싱이 죽지 않게 한다.
 * Firestore 가 리플렉션으로 채우므로 R8 이 속성을 지우거나 이름을 바꾸면 안 된다(@Keep).
 */
@Keep
data class UserDto(
    val nickname: String? = null,
    val nicknameLower: String? = null,
    val color: Long? = null,
    val cellCount: Long? = null,
)

interface UserDataSource {
    /** 문서가 없으면 null 을 흘린다. 스냅샷 에러는 예외로 닫는다. */
    fun observe(uid: String): Flow<UserDto?>

    /** `users orderBy cellCount desc limit n` 일회성 읽기. (uid, dto) 순서 유지. 실패는 DataSourceException. */
    suspend fun topByCellCount(limit: Int): List<Pair<String, UserDto>>

    /** `users where cellCount > than` 의 count 집계(1000문서당 읽기 1). */
    suspend fun countWithMoreCells(than: Int): Int

    /** `users/{uid}` 일회성 읽기. 문서 없음 → null. 실패는 DataSourceException. */
    suspend fun get(uid: String): UserDto?
}
