package com.jaychoi.eattheland.core.data.walk

import com.jaychoi.eattheland.core.model.WalkSummary

/** 스펙 C §8 이력 저장. 한 번 시도하고 실패는 버린다(큐 없음, 사용자 결정 6). 예외를 던지지 않는다. */
interface WalkRepository {
    /** true = 서버가 받았다. false = 로그인 전·오프라인·규칙 거부·시간 초과. */
    suspend fun save(summary: WalkSummary): Boolean
}
