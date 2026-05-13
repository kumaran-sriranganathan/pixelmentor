package com.pixelmentor.app.domain.model

import com.google.gson.annotations.SerializedName

// ── Chat ──────────────────────────────────────────────────────────────────────

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: String,   // "user" | "assistant"
    val content: String,
    val isStreaming: Boolean = false
)

data class TutorChatRequest(
    @SerializedName("message") val message: String,
    @SerializedName("topic") val topic: String? = null
)

// ── Quiz ──────────────────────────────────────────────────────────────────────

data class QuizRequest(
    @SerializedName("topic") val topic: String,
    @SerializedName("difficulty") val difficulty: String = "intermediate",
    @SerializedName("num_questions") val numQuestions: Int = 5
)

data class QuizResponse(
    @SerializedName("quiz_id") val quizId: String,
    @SerializedName("topic") val topic: String,
    @SerializedName("questions") val questions: List<QuizQuestion>
)

data class QuizQuestion(
    @SerializedName("id") val id: String,
    @SerializedName("question") val question: String,
    @SerializedName("options") val options: List<String>,
    @SerializedName("correct_index") val correctIndex: Int,
    @SerializedName("explanation") val explanation: String
)

// ── UI State ──────────────────────────────────────────────────────────────────

sealed class TutorUiState {
    object Idle : TutorUiState()
    object Loading : TutorUiState()
    data class Error(val message: String) : TutorUiState()
}

sealed class QuizUiState {
    object Idle : QuizUiState()
    object Loading : QuizUiState()
    data class Active(
        val quiz: QuizResponse,
        val currentIndex: Int = 0,
        val selectedAnswer: Int? = null,
        val showExplanation: Boolean = false,
        val score: Int = 0,
        val completed: Boolean = false
    ) : QuizUiState()
    data class Error(val message: String) : QuizUiState()
}

enum class TutorTab { CHAT, QUIZ }

val photographyTopics = listOf(
    "Composition",
    "Lighting",
    "Exposure",
    "Depth of Field",
    "Color Theory",
    "Post-Processing",
    "Portrait Photography",
    "Landscape Photography",
    "Street Photography",
    "Night Photography"
)
