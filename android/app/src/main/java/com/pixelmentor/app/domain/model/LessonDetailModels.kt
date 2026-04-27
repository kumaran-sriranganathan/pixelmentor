package com.pixelmentor.app.domain.model

data class LessonDetail(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val skillLevel: SkillLevel,
    val durationMinutes: Int,
    val isPro: Boolean,
    val tags: List<String>,
    val content: String,
)

sealed class LessonDetailUiState {
    object Loading : LessonDetailUiState()
    data class Success(val lesson: LessonDetail) : LessonDetailUiState()
    data class Error(val message: String) : LessonDetailUiState()
    object ProRequired : LessonDetailUiState()
}