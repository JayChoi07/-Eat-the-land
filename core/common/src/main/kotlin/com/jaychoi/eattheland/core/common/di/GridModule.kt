package com.jaychoi.eattheland.core.common.di

import com.jaychoi.eattheland.core.common.grid.H3HexGrid
import com.jaychoi.eattheland.core.common.grid.HexGrid
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface GridModule {
    @Binds
    fun bindHexGrid(impl: H3HexGrid): HexGrid
}
