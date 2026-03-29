package com.pixelmentor.app.data.auth

import android.app.Activity
import com.microsoft.identity.client.exception.MsalUiRequiredException
import com.pixelmentor.app.domain.model.AuthState
import com.pixelmentor.app.domain.model.AuthUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val msalAuthManager: MsalAuthManager,
) {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    val currentToken: String?
        get() = (_authState.value as? AuthState.Authenticated)?.user?.accessToken

    /**
     * Called at app start — restores session from MSAL cache if available.
     */
    suspend fun restoreSession() {
        val user = msalAuthManager.getCurrentAccount()
        _authState.value = if (user != null) {
            AuthState.Authenticated(user)
        } else {
            AuthState.Unauthenticated
        }
    }

    suspend fun signIn(activity: Activity) {
        val user = msalAuthManager.signIn(activity)
        _authState.value = AuthState.Authenticated(user)
    }

    /**
     * Silent token refresh — called by the OkHttp interceptor when the
     * backend returns error=token_expired.
     * Returns the new access token, or null if interactive re-login is needed.
     */
    suspend fun refreshToken(): String? {
        return try {
            val user = msalAuthManager.acquireTokenSilently()
            _authState.value = AuthState.Authenticated(user)
            user.accessToken
        } catch (e: MsalUiRequiredException) {
            // Refresh token expired — user must log in again
            _authState.value = AuthState.Unauthenticated
            null
        }
    }

    suspend fun signOut() {
        msalAuthManager.signOut()
        _authState.value = AuthState.Unauthenticated
    }
}
