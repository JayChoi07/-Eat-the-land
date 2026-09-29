package com.jaychoi.eattheland.core.common

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** 벽시계. 만료 계산을 테스트에서 고정하려고 주입한다. */
fun interface Clock {
    fun nowMillis(): Long
}

@Module
@InstallIn(SingletonComponent::class)
object ClockModule {
    @Provides
    fun provideClock(): Clock = Clock { System.currentTimeMillis() }
}
