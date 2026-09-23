package com.jaychoi.eattheland.core.common

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** 이 파일만 :core:common 모듈로 간다. feature 모듈이 아니다. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class DefaultDispatcher

/**
 * R-22-02(디스패처는 주입받는다)를 강제하는 detekt 규칙 `InjectDispatcher`는
 * `Dispatchers.IO`·`Dispatchers.Default` 참조를 전부 잡는다. 그 규칙이 성립하려면
 * 프로젝트에 "실제로 디스패처를 만드는 곳"이 딱 한 군데 있어야 하고, 여기가 그 한 곳이다.
 * 그래서 전역 비활성화 대신 이 두 함수에만 억제를 건다.
 */
@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {
    @Provides
    @IoDispatcher
    @Suppress("InjectDispatcher")
    fun providesIo(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    @Suppress("InjectDispatcher")
    fun providesDefault(): CoroutineDispatcher = Dispatchers.Default
}
