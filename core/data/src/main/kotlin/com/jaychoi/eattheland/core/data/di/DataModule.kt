package com.jaychoi.eattheland.core.data.di

import com.jaychoi.eattheland.core.data.DefaultPlayerRepository
import com.jaychoi.eattheland.core.data.PlayerRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface DataModule {
    @Binds fun bindPlayerRepository(impl: DefaultPlayerRepository): PlayerRepository
}
