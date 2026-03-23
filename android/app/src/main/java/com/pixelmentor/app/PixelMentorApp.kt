package com.pixelmentor.app

import android.app.Application
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
        // Restore MSAL session on app start so the user isn't asked to
        // sign in again if they already have a valid session
        appScope.launch {
            authRepository.restoreSession()
        }
    }
}
