package com.pixelmentor.app.ui.analyze

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixelmentor.app.data.repository.AnalysisRepository
import com.pixelmentor.app.domain.model.AnalysisUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PhotoAnalysisViewModel @Inject constructor(
    private val repository: AnalysisRepository
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

            // Short delay so "Preparing image…" message is visible before heavy work
            kotlinx.coroutines.delay(400)
            _uiState.value = AnalysisUiState.Analyzing

            repository.analyzePhoto(uri).fold(
                onSuccess = { _uiState.value = AnalysisUiState.Success(it) },
                onFailure = { _uiState.value = AnalysisUiState.Error(it.message ?: "Analysis failed") }
            )
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
