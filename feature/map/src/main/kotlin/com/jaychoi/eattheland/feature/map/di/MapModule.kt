package com.jaychoi.eattheland.feature.map.di

import com.jaychoi.eattheland.feature.map.data.DefaultMapLocalDataSource
import com.jaychoi.eattheland.feature.map.data.DefaultMapRemoteDataSource
import com.jaychoi.eattheland.feature.map.data.DefaultMapRepository
import com.jaychoi.eattheland.feature.map.data.MapLocalDataSource
import com.jaychoi.eattheland.feature.map.data.MapRemoteDataSource
import com.jaychoi.eattheland.feature.map.data.MapRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** @Binds 만 있는 모듈은 abstract class 가 아니라 interface 로 둔다(구체 멤버가 없다). */
@Module
@InstallIn(SingletonComponent::class)
interface MapModule {
    @Binds
    fun bindMapRepository(impl: DefaultMapRepository): MapRepository

    @Binds
    fun bindMapRemoteDataSource(
        impl: DefaultMapRemoteDataSource,
    ): MapRemoteDataSource

    @Binds
    fun bindMapLocalDataSource(
        impl: DefaultMapLocalDataSource,
    ): MapLocalDataSource
}
