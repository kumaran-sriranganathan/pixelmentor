package com.pixelmentor.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixelmentor.app.data.auth.AuthRepository
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
    private val authManager: SupabaseAuthManager,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _deleteAccountState = MutableStateFlow<DeleteAccountState>(DeleteAccountState.Idle)
    val deleteAccountState: StateFlow<DeleteAccountState> = _deleteAccountState.asStateFlow()

    private val _userEmail = MutableStateFlow<String>("")
    val userEmail: StateFlow<String> = _userEmail.asStateFlow()

    private var loadedUserId: String? = null

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading

            val user = authManager.getCurrentUser()
            if (user == null) {
                _uiState.value = ProfileUiState.Error("Not signed in")
                loadedUserId = null
                _userEmail.value = ""
                return@launch
            }

            if (loadedUserId == user.id && _uiState.value is ProfileUiState.Success) {
                return@launch
            }

            if (loadedUserId != null && loadedUserId != user.id) {
                _uiState.value = ProfileUiState.Loading
                _userEmail.value = ""
            }

            loadedUserId = user.id
            _userEmail.value = user.email ?: ""

            repository.getProfile(user.id).fold(
                onSuccess = { _uiState.value = ProfileUiState.Success(it) },
                onFailure = { error ->
                    val message = when {
                        error is java.net.SocketTimeoutException ||
                        error.message?.contains("timeout", ignoreCase = true) == true ->
                            "Connection timed out. Check your internet and tap Retry."
                        error.message?.contains("504") == true ->
                            "Server took too long to respond. Please retry in a moment."
                        error.message?.contains("Unable to resolve host") == true ->
                            "No internet connection. Please check your network."
                        else -> error.message ?: "Failed to load profile"
                    }
                    _uiState.value = ProfileUiState.Error(message)
                }
            )
        }
    }

    fun signOut() {
        // ── Instant sign-out — no coroutine, no network, no hang ─────────────
        // notifyForceLogout() does two things synchronously:
        //   1. Sets _authState.value = Unauthenticated (a MutableStateFlow
        //      assignment — completes in nanoseconds on the calling thread)
        //   2. Emits to forceLogout SharedFlow (caught in MainActivity but
        //      not needed here — the authState change alone triggers the
        //      PixelMentorRoot when/Unauthenticated branch instantly)
        //
        // The Supabase session is cleared in the background after the user
        // is already on the Login screen — they never see a hang.
        authRepository.notifyForceLogout("") // empty string = no banner message

        // Clear session in background — fire and forget, user is already gone
        viewModelScope.launch {
            try { authManager.signOut() } catch (_: Exception) { }
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
                    authRepository.notifyForceLogout("")
                    viewModelScope.launch {
                        try { authManager.signOut() } catch (_: Exception) { }
                    }
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
