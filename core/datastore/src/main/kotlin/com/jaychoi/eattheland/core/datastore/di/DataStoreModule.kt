package com.jaychoi.eattheland.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.jaychoi.eattheland.core.common.IoDispatcher
import com.jaychoi.eattheland.core.datastore.DataStorePendingCaptureDataSource
import com.jaychoi.eattheland.core.datastore.PendingCaptureDataSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/** DataStore 는 파일당 인스턴스 하나여야 한다 — 싱글턴 (R-14-06). */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun providePendingCaptureStore(
        @ApplicationContext context: Context,
        @IoDispatcher io: CoroutineDispatcher,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(io + SupervisorJob()),
    ) { context.preferencesDataStoreFile("pending_captures") }
}

@Module
@InstallIn(SingletonComponent::class)
interface PendingCaptureModule {
    @Binds
    fun bindPendingCapture(impl: DataStorePendingCaptureDataSource): PendingCaptureDataSource
}
