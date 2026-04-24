package com.pixelmentor.app.ui.tutor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixelmentor.app.data.auth.SupabaseAuthManager
import com.pixelmentor.app.data.repository.TutorRepository
import com.pixelmentor.app.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class TutorViewModel @Inject constructor(
    private val repository: TutorRepository,
    private val authManager: SupabaseAuthManager
) : ViewModel() {

    // ── Chat state ────────────────────────────────────────────────────────────

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _tutorState = MutableStateFlow<TutorUiState>(TutorUiState.Idle)
    val tutorState: StateFlow<TutorUiState> = _tutorState.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    // ── Quiz state ────────────────────────────────────────────────────────────

    private val _quizState = MutableStateFlow<QuizUiState>(QuizUiState.Idle)
    val quizState: StateFlow<QuizUiState> = _quizState.asStateFlow()

    private val _selectedTopic = MutableStateFlow(photographyTopics.first())
    val selectedTopic: StateFlow<String> = _selectedTopic.asStateFlow()

    // ── Tab ───────────────────────────────────────────────────────────────────

    private val _activeTab = MutableStateFlow(TutorTab.CHAT)
    val activeTab: StateFlow<TutorTab> = _activeTab.asStateFlow()

    // ── Chat actions ──────────────────────────────────────────────────────────

    fun onInputChanged(text: String) {
        _inputText.value = text
    }

    fun sendMessage() {
        val text = _inputText.value.trim()
        if (text.isEmpty() || _tutorState.value == TutorUiState.Loading) return

        _inputText.value = ""

        // Add user message
        val userMessage = ChatMessage(role = "user", content = text)
        _messages.update { it + userMessage }

        // Add placeholder assistant message for streaming
        val assistantId = UUID.randomUUID().toString()
        val assistantPlaceholder = ChatMessage(
            id = assistantId,
            role = "assistant",
            content = "",
            isStreaming = true
        )
        _messages.update { it + assistantPlaceholder }
        _tutorState.value = TutorUiState.Loading

        viewModelScope.launch {
            try {
                val token = authManager.getCurrentUser()?.accessToken ?: throw Exception("Not authenticated")
                val sb = StringBuilder()

                repository.streamChat(
                    message = text,
                    authToken = token
                ).collect { chunk ->
                    sb.append(chunk)
                    // Update the streaming message in place
                    _messages.update { messages ->
                        messages.map { msg ->
                            if (msg.id == assistantId) msg.copy(content = sb.toString())
                            else msg
                        }
                    }
                }

                // Mark streaming complete
                _messages.update { messages ->
                    messages.map { msg ->
                        if (msg.id == assistantId) msg.copy(isStreaming = false)
                        else msg
                    }
                }
                _tutorState.value = TutorUiState.Idle

            } catch (e: Exception) {
                // Remove placeholder and show error
                _messages.update { it.filter { msg -> msg.id != assistantId } }
                _tutorState.value = TutorUiState.Error(e.message ?: "Failed to get response")
            }
        }
    }

    // ── Quiz actions ──────────────────────────────────────────────────────────

    fun onTopicSelected(topic: String) {
        _selectedTopic.value = topic
    }

    fun startQuiz() {
        val topic = _selectedTopic.value
        _quizState.value = QuizUiState.Loading

        viewModelScope.launch {
            repository.generateQuiz(topic = topic).fold(
                onSuccess = { quiz ->
                    _quizState.value = QuizUiState.Active(quiz = quiz)
                },
                onFailure = {
                    _quizState.value = QuizUiState.Error(it.message ?: "Failed to generate quiz")
                }
            )
        }
    }

    fun selectAnswer(index: Int) {
        val state = _quizState.value as? QuizUiState.Active ?: return
        if (state.selectedAnswer != null) return // Already answered
        _quizState.value = state.copy(
            selectedAnswer = index,
            showExplanation = true
        )
    }

    fun nextQuestion() {
        val state = _quizState.value as? QuizUiState.Active ?: return
        val isCorrect = state.selectedAnswer == state.quiz.questions[state.currentIndex].correctIndex
        val newScore = if (isCorrect) state.score + 1 else state.score
        val nextIndex = state.currentIndex + 1

        if (nextIndex >= state.quiz.questions.size) {
            _quizState.value = state.copy(
                score = newScore,
                completed = true,
                showExplanation = false,
                selectedAnswer = null
            )
        } else {
            _quizState.value = state.copy(
                currentIndex = nextIndex,
                selectedAnswer = null,
                showExplanation = false,
                score = newScore
            )
        }
    }

    fun resetQuiz() {
        _quizState.value = QuizUiState.Idle
    }

    fun setTab(tab: TutorTab) {
        _activeTab.value = tab
    }
}
