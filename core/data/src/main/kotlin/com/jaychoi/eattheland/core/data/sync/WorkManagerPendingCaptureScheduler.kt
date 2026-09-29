package com.jaychoi.eattheland.core.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * 연결이 돌아오면 [PendingCaptureWorker] 를 한 번 돌린다. APPEND_OR_REPLACE 라 이미 도는 중에 새 항목이 생겨도
 * 한 번 더 돈다(KEEP 이면 도중 추가분이 다음 예약까지 남는다).
 */
class WorkManagerPendingCaptureScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : PendingCaptureScheduler {
    override fun scheduleFlush() {
        val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = OneTimeWorkRequestBuilder<PendingCaptureWorker>()
            .setConstraints(online)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    private companion object {
        const val WORK_NAME = "pending-captures"
        const val BACKOFF_SECONDS = 30L
    }
}
