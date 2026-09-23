package com.jaychoi.eattheland.core.network.di

import com.jaychoi.eattheland.core.network.AuthDataSource
import com.jaychoi.eattheland.core.network.FirebaseAuthDataSource
import com.jaychoi.eattheland.core.network.FirestoreNicknameDataSource
import com.jaychoi.eattheland.core.network.FirestoreUserDataSource
import com.jaychoi.eattheland.core.network.NicknameDataSource
import com.jaychoi.eattheland.core.network.UserDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface NetworkModule {
    @Binds fun bindAuth(impl: FirebaseAuthDataSource): AuthDataSource

    @Binds fun bindUser(impl: FirestoreUserDataSource): UserDataSource

    @Binds fun bindNickname(impl: FirestoreNicknameDataSource): NicknameDataSource
}
