package com.pixelmentor.app

import android.app.Application
import android.util.Log
import com.pixelmentor.app.data.auth.AuthRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PixelMentorApp : Application() {

    @Inject
    lateinit var authRepository: AuthRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            try {
                authRepository.restoreSession()
            } catch (e: Exception) {
                Log.e("PixelMentorApp", "Failed to restore session: ${e.message}")
            }
        }
    }
}