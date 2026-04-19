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
                Log.e("LoginViewModel", "Sign in failed: ${e.message}")
                _uiState.update { LoginUiState.Error(e.message ?: "Sign in failed") }
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
                Log.e("LoginViewModel", "Google sign in failed: ${e.message}")
                _uiState.update { LoginUiState.Error(e.message ?: "Google sign in failed") }
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
                Log.e("LoginViewModel", "Sign up failed: ${e.message}")
                _uiState.update { LoginUiState.Error(e.message ?: "Sign up failed") }
            }
        }
    }
}