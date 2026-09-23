package com.jaychoi.eattheland.core.domain

import javax.inject.Inject

/** 서버 `functions/src/nickname.ts` 의 NICKNAME_RE 와 같은 규칙. 온보딩·설정 두 화면이 쓴다 (R-16-07). */
class ValidateNicknameUseCase @Inject constructor() {
    operator fun invoke(nickname: String): Boolean = NICKNAME_REGEX.matches(nickname)

    private companion object {
        val NICKNAME_REGEX = Regex("^[가-힣a-zA-Z0-9]{2,12}$")
    }
}
