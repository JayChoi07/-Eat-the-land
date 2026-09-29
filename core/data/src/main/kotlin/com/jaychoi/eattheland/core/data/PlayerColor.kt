package com.jaychoi.eattheland.core.data

private const val COLOR_COUNT = 7

/** 스펙 §4: 서버 카운터가 없으므로 uid 해시로 0..6 배정. 프로필 생성과 셀 캡처가 같은 값을 쓴다. */
internal fun colorFor(uid: String): Int = uid.hashCode().mod(COLOR_COUNT)
