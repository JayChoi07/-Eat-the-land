package com.jaychoi.eattheland.core.model

sealed interface PlayerError {
    data object Network : PlayerError
    data object NicknameTaken : PlayerError
    data object InvalidNickname : PlayerError
    data class Unknown(val cause: Throwable) : PlayerError
}
