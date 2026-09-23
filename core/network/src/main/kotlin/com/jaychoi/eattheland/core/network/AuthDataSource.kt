package com.jaychoi.eattheland.core.network

import kotlinx.coroutines.flow.Flow

interface AuthDataSource {
    /** 현재 uid. 로그아웃/미로그인이면 null. */
    val uid: Flow<String?>

    /** 익명 로그인을 보장하고 uid 를 돌려준다. 실패는 예외로 던진다(Repository 가 잡는다). */
    suspend fun ensureSignedIn(): String
}
