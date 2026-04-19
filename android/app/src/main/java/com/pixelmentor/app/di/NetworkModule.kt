package com.pixelmentor.app.di

import com.pixelmentor.app.BuildConfig
import com.pixelmentor.app.data.api.AuthInterceptor
import com.pixelmentor.app.data.api.PixelMentorApiService
import com.pixelmentor.app.data.api.TokenRefreshInterceptor
import com.pixelmentor.app.data.auth.AuthRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideAuthInterceptor(authRepository: AuthRepository): AuthInterceptor =
        AuthInterceptor(authRepository)

    @Provides
    @Singleton
    fun provideTokenRefreshInterceptor(authRepository: AuthRepository): TokenRefreshInterceptor =
        TokenRefreshInterceptor(authRepository)

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        tokenRefreshInterceptor: TokenRefreshInterceptor,
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .followSslRedirects(true)
            .followRedirects(false)
            // Auth interceptor runs first — attaches token
            .addInterceptor(authInterceptor)
            // Refresh interceptor runs after response — handles token_expired
            .addInterceptor(tokenRefreshInterceptor)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) {
                        HttpLoggingInterceptor.Level.BODY
                    } else {
                        HttpLoggingInterceptor.Level.NONE
                    }
                }
            )
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        val baseUrl = if (BuildConfig.USE_DEV_API) {
            BuildConfig.API_BASE_URL_DEV
        } else {
            BuildConfig.API_BASE_URL_PROD
        }
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): PixelMentorApiService =
        retrofit.create(PixelMentorApiService::class.java)
}
