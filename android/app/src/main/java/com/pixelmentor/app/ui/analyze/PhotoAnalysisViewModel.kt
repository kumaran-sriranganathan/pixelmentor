package com.pixelmentor.app.ui.analyze

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixelmentor.app.data.auth.SupabaseAuthManager
import com.pixelmentor.app.data.repository.AnalysisRepository
import com.pixelmentor.app.domain.model.AnalysisUiState
import com.pixelmentor.app.domain.model.PhotoLimitException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PhotoAnalysisViewModel @Inject constructor(
    private val repository: AnalysisRepository,
    private val authManager: SupabaseAuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<AnalysisUiState>(AnalysisUiState.Idle)
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()

    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageUri: StateFlow<Uri?> = _selectedImageUri.asStateFlow()

    fun onImageSelected(uri: Uri) {
        _selectedImageUri.value = uri
        _uiState.value = AnalysisUiState.Idle
    }

    fun analyzePhoto() {
        val uri = _selectedImageUri.value ?: return
        viewModelScope.launch {
            _uiState.value = AnalysisUiState.Uploading
            kotlinx.coroutines.delay(400)
            _uiState.value = AnalysisUiState.Analyzing

            try {
                val token = authManager.getCurrentUser()?.accessToken
                    ?: throw Exception("Not authenticated")
                repository.analyzePhoto(uri, token).fold(
                    onSuccess = { _uiState.value = AnalysisUiState.Success(it) },
                    onFailure = { error ->
                        if (error is PhotoLimitException) {
                            _uiState.value = AnalysisUiState.LimitReached(
                                used = error.used,
                                limit = error.limit,
                                plan = error.plan,
                                upgradeRequired = error.upgradeRequired
                            )
                        } else {
                            _uiState.value = AnalysisUiState.Error(
                                error.message ?: "Analysis failed"
                            )
                        }
                    }
                )
            } catch (e: PhotoLimitException) {
                _uiState.value = AnalysisUiState.LimitReached(
                    used = e.used,
                    limit = e.limit,
                    plan = e.plan,
                    upgradeRequired = e.upgradeRequired
                )
            } catch (e: Exception) {
                _uiState.value = AnalysisUiState.Error(e.message ?: "Not authenticated")
            }
        }
    }

    fun reset() {
        _uiState.value = AnalysisUiState.Idle
        _selectedImageUri.value = null
    }

    fun retryAnalysis() {
        _uiState.value = AnalysisUiState.Idle
    }
}
