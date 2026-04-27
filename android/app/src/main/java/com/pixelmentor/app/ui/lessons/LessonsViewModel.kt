package com.pixelmentor.app.ui.lessons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixelmentor.app.data.auth.AuthRepository
import com.pixelmentor.app.data.repository.LessonsRepository
import com.pixelmentor.app.domain.model.AppException
import com.pixelmentor.app.domain.model.AuthState
import com.pixelmentor.app.domain.model.Lesson
import com.pixelmentor.app.domain.model.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class LessonsUiState {
    data object Loading : LessonsUiState()
    data class Success(val lessons: List<Lesson>) : LessonsUiState()
    data class Error(val message: String) : LessonsUiState()
    data object Unauthorized : LessonsUiState()
}

@HiltViewModel
class LessonsViewModel @Inject constructor(
    private val lessonsRepository: LessonsRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LessonsUiState>(LessonsUiState.Loading)
    val uiState: StateFlow<LessonsUiState> = _uiState.asStateFlow()

    init {
        // Watch auth state — redirect to login if user is signed out
        authRepository.authState
            .onEach { state ->
                if (state is AuthState.Unauthenticated) {
                    _uiState.value = LessonsUiState.Unauthorized
                }
            }
            .launchIn(viewModelScope)

        loadLessons()
    }

    fun loadLessons() {
        viewModelScope.launch {
            _uiState.value = LessonsUiState.Loading

            // Wait until auth state is no longer Loading before making API call
            // This ensures restoreSession() has completed and token is available
            val authState = authRepository.authState
                .first { it !is AuthState.Loading }

            if (authState is AuthState.Unauthenticated) {
                _uiState.value = LessonsUiState.Unauthorized
                return@launch
            }

            when (val result = lessonsRepository.getLessons()) {
                is Result.Success -> _uiState.value = LessonsUiState.Success(result.data)
                is Result.Error -> {
                    when (result.exception) {
                        is AppException.Unauthorized -> _uiState.value = LessonsUiState.Unauthorized
                        else -> _uiState.value = LessonsUiState.Error(
                            result.exception.message ?: "Failed to load lessons"
                        )
                    }
                }
            }
        }
    }
}
