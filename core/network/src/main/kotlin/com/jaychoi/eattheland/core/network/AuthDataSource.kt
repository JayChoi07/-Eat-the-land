package com.jaychoi.eattheland.core.network

import kotlinx.coroutines.flow.Flow

interface AuthDataSource {
    /** 현재 uid. 로그아웃/미로그인이면 null. */
    val uid: Flow<String?>

    /** 익명 로그인을 보장하고 uid 를 돌려준다. 실패는 예외로 던진다(Repository 가 잡는다). */
    suspend fun ensureSignedIn(): String

    /** 현재 Auth 사용자를 지운다. 실패(재인증 요구·오프라인)는 예외. */
    suspend fun deleteCurrentUser()

    /** 로그아웃. uid 가 null 로 흐른다. */
    fun signOut()
}
