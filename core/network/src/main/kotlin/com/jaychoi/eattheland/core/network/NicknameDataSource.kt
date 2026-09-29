package com.jaychoi.eattheland.core.network

interface NicknameDataSource {
    /**
     * 스펙 §4 setNickname 트랜잭션. users 가 없으면 colorIfNew 로 생성한다.
     * 중복이면 DataSourceException(NicknameTaken), 규칙 경합(PERMISSION_DENIED)도 NicknameTaken 으로 본다.
     */
    suspend fun setNickname(uid: String, nickname: String, colorIfNew: Int)

    /**
     * 스펙 C §5: users/{uid} 와 nicknames/{lower} 를 한 배치로 지운다(규칙이 짝을 강제).
     * 프로필이 없으면 아무것도 안 한다.
     */
    suspend fun deleteProfile(uid: String)
}
