package com.pixelmentor.app.ui.lessons

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixelmentor.app.data.repository.LessonsRepository
import com.pixelmentor.app.domain.model.AppException
import com.pixelmentor.app.domain.model.LessonDetail
import com.pixelmentor.app.domain.model.LessonDetailUiState
import com.pixelmentor.app.domain.model.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LessonDetailViewModel @Inject constructor(
    private val repository: LessonsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val lessonId: String = checkNotNull(savedStateHandle["lessonId"])

    private val _uiState = MutableStateFlow<LessonDetailUiState>(LessonDetailUiState.Loading)
    val uiState: StateFlow<LessonDetailUiState> = _uiState.asStateFlow()

    private val _expandedContent = MutableStateFlow<String?>(null)
    val expandedContent: StateFlow<String?> = _expandedContent.asStateFlow()

    private val _isLoadingContent = MutableStateFlow(false)
    val isLoadingContent: StateFlow<Boolean> = _isLoadingContent.asStateFlow()

    // ── Completion state ──────────────────────────────────────────────────────
    private val _isCompleted = MutableStateFlow(false)
    val isCompleted: StateFlow<Boolean> = _isCompleted.asStateFlow()

    private val _isMarkingComplete = MutableStateFlow(false)
    val isMarkingComplete: StateFlow<Boolean> = _isMarkingComplete.asStateFlow()

    init {
        loadLesson()
    }

    fun loadLesson() {
        viewModelScope.launch {
            _uiState.value = LessonDetailUiState.Loading
            when (val result = repository.getLesson(lessonId)) {
                is Result.Success -> {
                    val lesson = result.data
                    if (lesson.isPro) {
                        _uiState.value = LessonDetailUiState.ProRequired
                    } else {
                        _uiState.value = LessonDetailUiState.Success(lesson)
                        loadExpandedContent(lesson)
                        checkCompletion()
                    }
                }
                is Result.Error -> {
                    _uiState.value = LessonDetailUiState.Error(
                        result.exception.message ?: "Failed to load lesson"
                    )
                }
            }
        }
    }

    private fun loadExpandedContent(lesson: LessonDetail) {
        viewModelScope.launch {
            _isLoadingContent.value = true
            when (val result = repository.getLessonContent(lessonId)) {
                is Result.Success -> _expandedContent.value = result.data
                is Result.Error -> {
                    if (result.exception is AppException.ProRequired) {
                        _uiState.value = LessonDetailUiState.ProRequired
                    } else {
                        _expandedContent.value = lesson.content
                    }
                }
            }
            _isLoadingContent.value = false
        }
    }

    private fun checkCompletion() {
        viewModelScope.launch {
            when (val result = repository.getCompletedLessonIds()) {
                is Result.Success -> _isCompleted.value = lessonId in result.data
                is Result.Error -> { /* non-fatal */ }
            }
        }
    }

    fun markComplete() {
        if (_isCompleted.value || _isMarkingComplete.value) return
        viewModelScope.launch {
            _isMarkingComplete.value = true
            when (repository.markLessonComplete(lessonId)) {
                is Result.Success -> _isCompleted.value = true
                is Result.Error -> { /* non-fatal */ }
            }
            _isMarkingComplete.value = false
        }
    }
}
