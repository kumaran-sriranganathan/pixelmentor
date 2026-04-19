package com.pixelmentor.app.data.auth

import android.app.Activity
import com.pixelmentor.app.domain.model.AuthState
import com.pixelmentor.app.domain.model.AuthUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val supabaseAuthManager: SupabaseAuthManager,
) {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

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

    suspend fun signInWithEmail(email: String, password: String) {
        val user = supabaseAuthManager.signInWithEmail(email, password)
        _authState.value = AuthState.Authenticated(user)
    }

    suspend fun signInWithGoogle() {
        val user = supabaseAuthManager.signInWithGoogle()
        _authState.value = AuthState.Authenticated(user)
    }

    suspend fun signUp(email: String, password: String) {
        val user = supabaseAuthManager.signUp(email, password)
        _authState.value = AuthState.Authenticated(user)
    }

    suspend fun refreshToken(): String? {
        return try {
            val user = supabaseAuthManager.refreshSession()
            if (user != null) {
                _authState.value = AuthState.Authenticated(user)
                user.accessToken
            } else {
                _authState.value = AuthState.Unauthenticated
                null
            }
        } catch (e: Exception) {
            _authState.value = AuthState.Unauthenticated
            null
        }
    }

    suspend fun signOut() {
        supabaseAuthManager.signOut()
        _authState.value = AuthState.Unauthenticated
    }
}