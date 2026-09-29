package com.jaychoi.eattheland.core.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jaychoi.eattheland.core.data.TerritoryRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** WorkManager 가 만드는 클래스라 생성자 주입이 안 된다 — @EntryPoint 로 얻는다 (R-14-09). */
class PendingCaptureWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun territoryRepository(): TerritoryRepository
    }

    override suspend fun doWork(): Result {
        val territory = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
            .territoryRepository()
        // 남은 게 있으면(도중 오프라인·일시 오류) 백오프 뒤 다시. 만료·한도는 큐가 스스로 정리한다.
        return if (territory.flushPending() == 0) Result.success() else Result.retry()
    }
}
