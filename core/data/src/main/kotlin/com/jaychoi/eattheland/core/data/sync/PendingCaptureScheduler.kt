package com.jaychoi.eattheland.core.data.sync

/** 큐에 항목이 생기면 "연결되면 flushPending 을 돌려라"를 예약한다. 구현은 WorkManager. */
interface PendingCaptureScheduler {
    fun scheduleFlush()
}
