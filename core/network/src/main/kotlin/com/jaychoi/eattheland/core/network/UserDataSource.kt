package com.jaychoi.eattheland.core.network

import kotlinx.coroutines.flow.Flow

/** Firestore `users/{uid}` 문서. 필드는 전부 nullable — 서버 스키마 변경에 파싱이 죽지 않게 한다. */
data class UserDto(
    val nickname: String? = null,
    val nicknameLower: String? = null,
    val color: Long? = null,
    val cellCount: Long? = null,
)

interface UserDataSource {
    /** 문서가 없으면 null 을 흘린다. 스냅샷 에러는 예외로 닫는다. */
    fun observe(uid: String): Flow<UserDto?>
}
