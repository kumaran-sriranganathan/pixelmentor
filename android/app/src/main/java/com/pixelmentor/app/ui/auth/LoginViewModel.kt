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

// ── UI state ──────────────────────────────────────────────────────────────────

sealed class LoginUiState {
    data object Idle : LoginUiState()
    data object SigningIn : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

sealed class ForgotPasswordUiState {
    data object Idle : ForgotPasswordUiState()
    data object Sending : ForgotPasswordUiState()
    data object EmailSent : ForgotPasswordUiState()
    data class Error(val message: String) : ForgotPasswordUiState()
}

sealed class ResetPasswordUiState {
    data object Idle : ResetPasswordUiState()
    data object Saving : ResetPasswordUiState()
    data object Success : ResetPasswordUiState()
    data class Error(val message: String) : ResetPasswordUiState()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.authState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthState.Loading)

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState

    private val _forgotPasswordState = MutableStateFlow<ForgotPasswordUiState>(ForgotPasswordUiState.Idle)
    val forgotPasswordState: StateFlow<ForgotPasswordUiState> = _forgotPasswordState

    private val _resetPasswordState = MutableStateFlow<ResetPasswordUiState>(ResetPasswordUiState.Idle)
    val resetPasswordState: StateFlow<ResetPasswordUiState> = _resetPasswordState

    // ── Sign in / sign up ─────────────────────────────────────────────────────

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

    // ── Forgot password ───────────────────────────────────────────────────────

    fun sendPasswordResetEmail(email: String) {
        if (email.isBlank()) {
            _forgotPasswordState.update {
                ForgotPasswordUiState.Error("Please enter your email address.")
            }
            return
        }
        viewModelScope.launch {
            _forgotPasswordState.update { ForgotPasswordUiState.Sending }
            try {
                authRepository.sendPasswordResetEmail(email)
                _forgotPasswordState.update { ForgotPasswordUiState.EmailSent }
            } catch (e: Exception) {
                Log.e("LoginViewModel", "Password reset failed", e)
                _forgotPasswordState.update {
                    ForgotPasswordUiState.Error(friendlyResetError(e))
                }
            }
        }
    }

    fun resetForgotPasswordState() {
        _forgotPasswordState.update { ForgotPasswordUiState.Idle }
    }

    // ── Reset password (after deep-link) ──────────────────────────────────────

    fun updatePassword(newPassword: String) {
        viewModelScope.launch {
            _resetPasswordState.update { ResetPasswordUiState.Saving }
            try {
                authRepository.updatePassword(newPassword)
                _resetPasswordState.update { ResetPasswordUiState.Success }
            } catch (e: Exception) {
                Log.e("LoginViewModel", "Update password failed", e)
                _resetPasswordState.update {
                    ResetPasswordUiState.Error(friendlyAuthError(e, isSignUp = false))
                }
            }
        }
    }
}

// ── Error message mapping ─────────────────────────────────────────────────────

private fun friendlyAuthError(e: Exception, isSignUp: Boolean): String {
    val raw = e.message?.lowercase() ?: ""

    if (e is java.net.UnknownHostException ||
        e is java.net.ConnectException ||
        raw.contains("unable to resolve host") ||
        raw.contains("failed to connect")
    ) return "No internet connection. Please check your network and try again."

    if (e is java.net.SocketTimeoutException ||
        raw.contains("timeout") || raw.contains("timed out")
    ) return "The request timed out. Please check your connection and try again."

    if (raw.contains("invalid login credentials") || raw.contains("invalid_credentials"))
        return "Incorrect email or password. Please try again."

    if (raw.contains("email not confirmed") || raw.contains("email_not_confirmed"))
        return "Please verify your email address before signing in. Check your inbox for a confirmation link."

    if (raw.contains("user not found") || raw.contains("no user found"))
        return "No account found with that email address."

    if (raw.contains("user already registered") ||
        raw.contains("email already registered") ||
        raw.contains("email_exists")
    ) return "An account with this email already exists. Try signing in instead."

    if (raw.contains("password should be at least") || raw.contains("weak_password"))
        return "Password must be at least 6 characters long."

    if (raw.contains("unable to validate email") ||
        raw.contains("invalid email") ||
        raw.contains("email_invalid")
    ) return "Please enter a valid email address."

    if (raw.contains("too many requests") ||
        raw.contains("rate limit") ||
        raw.contains("429")
    ) return "Too many attempts. Please wait a moment and try again."

    if (raw.contains("signup_disabled") || raw.contains("signups not allowed"))
        return "New account registration is currently unavailable. Please try again later."

    return if (isSignUp)
        "Couldn't create your account. Please check your details and try again."
    else
        "Sign in failed. Please check your email and password and try again."
}

private fun friendlyResetError(e: Exception): String {
    val raw = e.message?.lowercase() ?: ""

    if (e is java.net.UnknownHostException || e is java.net.ConnectException)
        return "No internet connection. Please check your network and try again."

    if (raw.contains("user not found") || raw.contains("no user found"))
        return "No account found with that email address."

    if (raw.contains("too many requests") || raw.contains("rate limit") || raw.contains("429"))
        return "Too many attempts. Please wait a moment before trying again."

    if (raw.contains("unable to validate email") || raw.contains("email_invalid"))
        return "Please enter a valid email address."

    return "Couldn't send the reset email. Please try again."
}
