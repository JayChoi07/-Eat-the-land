package com.jaychoi.eattheland.core.data.di

import com.jaychoi.eattheland.core.data.DefaultPlayerRepository
import com.jaychoi.eattheland.core.data.DefaultTerritoryRepository
import com.jaychoi.eattheland.core.data.PlayerRepository
import com.jaychoi.eattheland.core.data.TerritoryRepository
import com.jaychoi.eattheland.core.data.location.DefaultLocationRepository
import com.jaychoi.eattheland.core.data.location.LocationRepository
import com.jaychoi.eattheland.core.data.ranking.DefaultRankingRepository
import com.jaychoi.eattheland.core.data.ranking.RankingRepository
import com.jaychoi.eattheland.core.data.sync.PendingCaptureScheduler
import com.jaychoi.eattheland.core.data.sync.WorkManagerPendingCaptureScheduler
import com.jaychoi.eattheland.core.data.tracking.DefaultTrackingRepository
import com.jaychoi.eattheland.core.data.tracking.TrackingRepository
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

    @Binds fun bindLocationRepository(impl: DefaultLocationRepository): LocationRepository

    @Binds fun bindTrackingRepository(impl: DefaultTrackingRepository): TrackingRepository

    @Binds fun bindRankingRepository(impl: DefaultRankingRepository): RankingRepository
}
