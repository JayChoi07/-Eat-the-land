package com.jaychoi.eattheland.core.datastore

import kotlinx.coroutines.flow.Flow

/** 통신이 끊긴 동안 밟은 셀. 한도·만료 정책은 :core:data 의 PendingCaptureQueue 가 정한다(R-23-10). */
data class PendingCapture(val cellId: String, val queuedAtMillis: Long)

interface PendingCaptureDataSource {
    /** 저장된 순서(오래된 것 먼저). */
    val pending: Flow<List<PendingCapture>>

    /** 현재 목록을 읽어 transform 결과로 통째로 바꾼다. 한 번의 원자적 쓰기다. */
    suspend fun update(transform: (List<PendingCapture>) -> List<PendingCapture>)
}
