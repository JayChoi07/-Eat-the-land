package com.jaychoi.eattheland.core.testing

import com.jaychoi.eattheland.core.data.sync.PendingCaptureScheduler

class FakePendingCaptureScheduler : PendingCaptureScheduler {
    var scheduled = 0
        private set

    override fun scheduleFlush() {
        scheduled++
    }
}
