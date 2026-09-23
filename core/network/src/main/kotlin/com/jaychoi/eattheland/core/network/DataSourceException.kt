package com.jaychoi.eattheland.core.network

/** 데이터소스가 던지는 유일한 예외. Firebase 예외는 여기로 변환돼 :core:data 가 Firebase 타입을 모르게 한다. */
class DataSourceException(val kind: Kind, cause: Throwable? = null) : Exception(kind.name, cause) {
    enum class Kind { NicknameTaken, Offline, PermissionDenied, Unknown }
}
