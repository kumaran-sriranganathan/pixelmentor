package com.pixelmentor.app.di

import android.content.Context
import com.pixelmentor.app.data.auth.AuthRepository
import com.pixelmentor.app.data.auth.MsalAuthManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun provideMsalAuthManager(
        @ApplicationContext context: Context,
    ): MsalAuthManager = MsalAuthManager(context)

    @Provides
    @Singleton
    fun provideAuthRepository(
        msalAuthManager: MsalAuthManager,
    ): AuthRepository = AuthRepository(msalAuthManager)
}
