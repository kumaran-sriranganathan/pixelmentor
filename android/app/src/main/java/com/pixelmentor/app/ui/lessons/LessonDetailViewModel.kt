package com.pixelmentor.app.ui.lessons

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixelmentor.app.data.repository.LessonsRepository
import com.pixelmentor.app.domain.model.AppException
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

    init {
        loadLesson()
    }

    fun loadLesson() {
        viewModelScope.launch {
            _uiState.value = LessonDetailUiState.Loading
            when (val result = repository.getLesson(lessonId)) {
                is Result.Success -> {
                    val lesson = result.data
                    // Pro gate — show locked state for pro lessons
                    // (billing not yet implemented, so all free tier users see ProRequired)
                    if (lesson.isPro) {
                        _uiState.value = LessonDetailUiState.ProRequired
                    } else {
                        _uiState.value = LessonDetailUiState.Success(lesson)
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
}
