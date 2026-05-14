package com.pixelmentor.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixelmentor.app.data.auth.SupabaseAuthManager
import com.pixelmentor.app.data.repository.ProfileRepository
import com.pixelmentor.app.domain.model.ProfileUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class DeleteAccountState {
    data object Idle : DeleteAccountState()
    data object Deleting : DeleteAccountState()
    data object Success : DeleteAccountState()
    data class Error(val message: String) : DeleteAccountState()
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: ProfileRepository,
    private val authManager: SupabaseAuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _deleteAccountState = MutableStateFlow<DeleteAccountState>(DeleteAccountState.Idle)
    val deleteAccountState: StateFlow<DeleteAccountState> = _deleteAccountState.asStateFlow()

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            val user = authManager.getCurrentUser()
            if (user == null) {
                _uiState.value = ProfileUiState.Error("Not signed in")
                return@launch
            }
            repository.getProfile(user.id).fold(
                onSuccess = { _uiState.value = ProfileUiState.Success(it) },
                onFailure = { _uiState.value = ProfileUiState.Error(it.message ?: "Failed to load profile") }
            )
        }
    }

    fun signOut(onSignedOut: () -> Unit) {
        viewModelScope.launch {
            authManager.signOut()
            onSignedOut()
        }
    }

    fun deleteAccount(onDeleted: () -> Unit) {
        viewModelScope.launch {
            _deleteAccountState.value = DeleteAccountState.Deleting
            val user = authManager.getCurrentUser()
            if (user == null) {
                _deleteAccountState.value = DeleteAccountState.Error("Not signed in")
                return@launch
            }
            repository.deleteAccount(user.id).fold(
                onSuccess = {
                    // Sign out locally after successful deletion
                    authManager.signOut()
                    _deleteAccountState.value = DeleteAccountState.Success
                    onDeleted()
                },
                onFailure = {
                    _deleteAccountState.value = DeleteAccountState.Error(
                        it.message ?: "Failed to delete account. Please try again."
                    )
                }
            )
        }
    }

    fun resetDeleteAccountState() {
        _deleteAccountState.value = DeleteAccountState.Idle
    }
}
