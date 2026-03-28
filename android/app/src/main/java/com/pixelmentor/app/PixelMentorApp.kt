package com.pixelmentor.app

import android.app.Application
import android.util.Log
import com.pixelmentor.app.data.auth.AuthRepository
import com.pixelmentor.app.data.auth.MsalAuthManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PixelMentorApp : Application() {

    @Inject
    lateinit var msalAuthManager: MsalAuthManager

    @Inject
    lateinit var authRepository: AuthRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            try {
                // Must initialise MSAL before restoring session
                msalAuthManager.initialize()
                authRepository.restoreSession()
            } catch (e: Exception) {
                Log.e("PixelMentorApp", "Failed to initialise MSAL: ${e.message}")
                // App continues — user will be prompted to sign in
            }
        }
    }
}
