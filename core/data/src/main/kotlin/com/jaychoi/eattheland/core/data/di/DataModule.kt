package com.jaychoi.eattheland.core.data.di

import com.jaychoi.eattheland.core.data.DefaultPlayerRepository
import com.jaychoi.eattheland.core.data.DefaultTerritoryRepository
import com.jaychoi.eattheland.core.data.PlayerRepository
import com.jaychoi.eattheland.core.data.TerritoryRepository
import com.jaychoi.eattheland.core.data.sync.PendingCaptureScheduler
import com.jaychoi.eattheland.core.data.sync.WorkManagerPendingCaptureScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface DataModule {
    @Binds fun bindPlayerRepository(impl: DefaultPlayerRepository): PlayerRepository

    @Binds fun bindTerritoryRepository(impl: DefaultTerritoryRepository): TerritoryRepository

    @Binds fun bindPendingCaptureScheduler(
        impl: WorkManagerPendingCaptureScheduler,
    ): PendingCaptureScheduler
}
