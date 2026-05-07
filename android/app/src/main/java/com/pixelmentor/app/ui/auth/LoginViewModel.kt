package com.pixelmentor.app.ui.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixelmentor.app.data.auth.AuthRepository
import com.pixelmentor.app.domain.model.AuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class LoginUiState {
    data object Idle : LoginUiState()
    data object SigningIn : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.authState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthState.Loading)

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState

    fun signInWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { LoginUiState.SigningIn }
            try {
                authRepository.signInWithEmail(email, password)
                _uiState.update { LoginUiState.Idle }
            } catch (e: Exception) {
                Log.e("LoginViewModel", "Sign in failed", e)
                _uiState.update { LoginUiState.Error(friendlyAuthError(e, isSignUp = false)) }
            }
        }
    }

    fun signInWithGoogle() {
        viewModelScope.launch {
            _uiState.update { LoginUiState.SigningIn }
            try {
                authRepository.signInWithGoogle()
                _uiState.update { LoginUiState.Idle }
            } catch (e: Exception) {
                Log.e("LoginViewModel", "Google sign in failed", e)
                _uiState.update {
                    LoginUiState.Error(friendlyAuthError(e, isSignUp = false, isGoogle = true))
                }
            }
        }
    }

    fun signUp(email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { LoginUiState.SigningIn }
            try {
                authRepository.signUp(email, password)
                _uiState.update { LoginUiState.Idle }
            } catch (e: Exception) {
                Log.e("LoginViewModel", "Sign up failed", e)
                _uiState.update { LoginUiState.Error(friendlyAuthError(e, isSignUp = true)) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Error message mapping
//
// Supabase GoTrue returns error messages embedded in the exception message
// string, e.g. "RestException: Invalid login credentials (400)".
// We match on substrings so we catch them regardless of the surrounding
// wrapper text the SDK adds.
// ─────────────────────────────────────────────────────────────────────────────

private fun friendlyAuthError(
    e: Exception,
    isSignUp: Boolean,
    isGoogle: Boolean = false,
): String {
    // Log the raw message for debugging — never shown to users
    val raw = e.message?.lowercase() ?: ""

    // ── Network / connectivity ────────────────────────────────────────────────
    if (e is java.net.UnknownHostException ||
        e is java.net.ConnectException ||
        raw.contains("unable to resolve host") ||
        raw.contains("failed to connect") ||
        raw.contains("no route to host")
    ) {
        return "No internet connection. Please check your network and try again."
    }

    if (e is java.net.SocketTimeoutException ||
        raw.contains("timeout") ||
        raw.contains("timed out")
    ) {
        return "The request timed out. Please check your connection and try again."
    }

    // ── Supabase / GoTrue error messages ─────────────────────────────────────
    // Wrong credentials (sign-in)
    if (raw.contains("invalid login credentials") ||
        raw.contains("invalid_credentials")
    ) {
        return "Incorrect email or password. Please try again."
    }

    // Email not verified
    if (raw.contains("email not confirmed") ||
        raw.contains("email_not_confirmed")
    ) {
        return "Please verify your email address before signing in. Check your inbox for a confirmation link."
    }

    // Account does not exist
    if (raw.contains("user not found") ||
        raw.contains("no user found")
    ) {
        return "No account found with that email address."
    }

    // Account already exists (sign-up)
    if (raw.contains("user already registered") ||
        raw.contains("email already registered") ||
        raw.contains("already been registered") ||
        raw.contains("email_exists")
    ) {
        return "An account with this email already exists. Try signing in instead."
    }

    // Weak password
    if (raw.contains("password should be at least") ||
        raw.contains("password is too short") ||
        raw.contains("weak_password")
    ) {
        return "Password must be at least 6 characters long."
    }

    // Invalid email format
    if (raw.contains("unable to validate email") ||
        raw.contains("invalid email") ||
        raw.contains("email_address_invalid") ||
        raw.contains("email_invalid")
    ) {
        return "Please enter a valid email address."
    }

    // Rate limited
    if (raw.contains("too many requests") ||
        raw.contains("over_email_send_rate_limit") ||
        raw.contains("rate limit") ||
        raw.contains("429")
    ) {
        return "Too many attempts. Please wait a moment and try again."
    }

    // Sign-ups disabled on this project
    if (raw.contains("signup_disabled") ||
        raw.contains("sign ups are disabled") ||
        raw.contains("signups not allowed")
    ) {
        return "New account registration is currently unavailable. Please try again later."
    }

    // Captcha required (Supabase hCaptcha)
    if (raw.contains("captcha") || raw.contains("hcaptcha")) {
        return "Verification required. Please try again."
    }

    // Session expired / token issue (shouldn't normally surface on login, but defensive)
    if (raw.contains("token expired") ||
        raw.contains("jwt expired") ||
        raw.contains("session_expired")
    ) {
        return "Your session has expired. Please sign in again."
    }

    // Google-specific fallback
    if (isGoogle) {
        return "Google sign-in failed. Please try again or use email instead."
    }

    // ── Generic fallback — never expose the raw SDK message ──────────────────
    return if (isSignUp) {
        "Couldn't create your account. Please check your details and try again."
    } else {
        "Sign in failed. Please check your email and password and try again."
    }
}
