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
        _authState.value = AuthState.Unauthenticated
        _forceLogout.tryEmit(reason)
    }

    val currentToken: String?
        get() = (_authState.value as? AuthState.Authenticated)?.user?.accessToken

    suspend fun restoreSession() {
        // ── Guard: never override an explicit sign-out ────────────────────────
        // signOut() calls clearSession() first, then sets Unauthenticated.
        // If restoreSession() runs after clearSession() but reads the Supabase
        // cache before Unauthenticated is set, it would bounce the user back to
        // Authenticated. This guard prevents that race entirely.
        if (_authState.value is AuthState.Unauthenticated) return

        val user = supabaseAuthManager.getCurrentUser()
        _authState.value = if (user != null) {
            AuthState.Authenticated(user)
        } else {
            AuthState.Unauthenticated
        }
    }

    suspend fun getValidToken(): String? {
        // Never return a token once signed out
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
        // ── Step 1: wipe Supabase in-memory session immediately ───────────────
        // clearSession() is a pure in-memory operation — no network call,
        // completes in microseconds. After this, getCurrentUser() returns null
        // and getValidToken() returns null (guarded by Unauthenticated check).
        // No race condition possible because there is no async network call.
        supabaseAuthManager.signOut()

        // ── Step 2: update auth state ─────────────────────────────────────────
        // PixelMentorRoot observes authState and swaps AppNavHost → AuthNavHost
        // the moment this is set. The user sees the login screen instantly.
        _authState.value = AuthState.Unauthenticated
    }
}
