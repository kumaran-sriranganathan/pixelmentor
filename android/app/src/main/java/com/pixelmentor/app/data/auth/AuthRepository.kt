package com.pixelmentor.app.data.auth

import com.pixelmentor.app.domain.model.AuthState
import com.pixelmentor.app.domain.model.AuthUser
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val supabaseAuthManager: SupabaseAuthManager,
) {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // ── Force-logout channel ──────────────────────────────────────────────────
    // Emitted by TokenRefreshInterceptor when a 401 survives token refresh.
    // MainActivity observes this and navigates to Login from anywhere in the app,
    // showing a "session expired" message rather than a raw error code.
    private val _forceLogout = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val forceLogout: SharedFlow<String> = _forceLogout.asSharedFlow()

    fun notifyForceLogout(reason: String = "Your session has expired. Please sign in again.") {
        // Mark state immediately so no refresh loop can race against this
        _authState.value = AuthState.Unauthenticated
        _forceLogout.tryEmit(reason)
    }

    val currentToken: String?
        get() = (_authState.value as? AuthState.Authenticated)?.user?.accessToken

    suspend fun restoreSession() {
        val user = supabaseAuthManager.getCurrentUser()
        _authState.value = if (user != null) {
            AuthState.Authenticated(user)
        } else {
            AuthState.Unauthenticated
        }
    }

    suspend fun getValidToken(): String? {
        // ── Guard: never return a token once signed out ───────────────────────
        // Without this, AuthInterceptor can still read a token from the Supabase
        // in-memory cache between signOut() being called and clearSession()
        // completing, causing post-signout requests to succeed and keeping the
        // user authenticated on the Lessons screen.
        if (_authState.value is AuthState.Unauthenticated) return null
        return try {
            currentToken ?: supabaseAuthManager.getCurrentUser()?.accessToken
        } catch (e: Exception) {
            null
        }
    }

    suspend fun signInWithEmail(email: String, password: String) {
        val user = supabaseAuthManager.signInWithEmail(email, password)
        _authState.value = AuthState.Authenticated(user)
    }

    suspend fun signUp(email: String, password: String) {
        val user = supabaseAuthManager.signUp(email, password)
        _authState.value = AuthState.Authenticated(user)
    }

    suspend fun sendPasswordResetEmail(email: String) {
        supabaseAuthManager.sendPasswordResetEmail(email)
    }

    /**
     * Called from MainActivity when a recovery deep link is received.
     * Establishes the session from the deep link tokens BEFORE updatePassword()
     * is called — without this the JWT has no sub claim and the update fails.
     */
    suspend fun handlePasswordResetDeepLink(deepLinkUrl: String) {
        supabaseAuthManager.handlePasswordResetDeepLink(deepLinkUrl)
    }

    suspend fun updatePassword(newPassword: String) {
        supabaseAuthManager.updatePassword(newPassword)
        val user = supabaseAuthManager.getCurrentUser()
        if (user != null) _authState.value = AuthState.Authenticated(user)
    }

    suspend fun refreshToken(): String? {
        return try {
            val user = supabaseAuthManager.refreshSession()
            if (user != null) {
                _authState.value = AuthState.Authenticated(user)
                user.accessToken
            } else {
                notifyForceLogout()
                null
            }
        } catch (e: Exception) {
            notifyForceLogout()
            null
        }
    }

    suspend fun signOut() {
        // ── Set state FIRST before calling Supabase ───────────────────────────
        // This blocks any concurrent TokenRefreshInterceptor call from racing
        // in and setting _authState back to Authenticated before Supabase's
        // signOut() network call completes.
        _authState.value = AuthState.Unauthenticated
        try {
            supabaseAuthManager.signOut()
        } catch (_: Exception) {
            // Session may already be invalid — state is already Unauthenticated,
            // so swallow the error and let navigation proceed.
        }
    }
}
