package com.pixelmentor.app.di

import com.pixelmentor.app.data.auth.AuthRepository
import com.pixelmentor.app.data.auth.SupabaseAuthManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun provideSupabaseAuthManager(): SupabaseAuthManager = SupabaseAuthManager()

    @Provides
    @Singleton
    fun provideAuthRepository(
        supabaseAuthManager: SupabaseAuthManager,
    ): AuthRepository = AuthRepository(supabaseAuthManager)
}