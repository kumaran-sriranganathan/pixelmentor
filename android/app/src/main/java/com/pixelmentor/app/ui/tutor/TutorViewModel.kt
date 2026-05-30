package com.pixelmentor.app.ui.tutor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixelmentor.app.data.auth.SupabaseAuthManager
import com.pixelmentor.app.data.repository.TutorRepository
import com.pixelmentor.app.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.HttpException
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class TutorViewModel @Inject constructor(
    private val repository: TutorRepository,
    private val authManager: SupabaseAuthManager
) : ViewModel() {

    // ── Session ───────────────────────────────────────────────────────────────
    // A new session ID is generated each time the ViewModel is created (i.e.
    // each time the user opens the Tutor screen). This scopes chat history to
    // the current conversation so GPT-4o doesn't bleed context across sessions.
    private val sessionId: String = UUID.randomUUID().toString()

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

    // ── Cache warming ─────────────────────────────────────────────────────────

    private val _quizCacheWarming = MutableStateFlow(false)
    val quizCacheWarming: StateFlow<Boolean> = _quizCacheWarming.asStateFlow()

    // ── Tab ───────────────────────────────────────────────────────────────────

    private val _activeTab = MutableStateFlow(TutorTab.CHAT)
    val activeTab: StateFlow<TutorTab> = _activeTab.asStateFlow()

    // ── Quiz upgrade dialog ───────────────────────────────────────────────────
    // Shown when the user hits their monthly quiz limit (HTTP 429).

    private val _showQuizUpgradeDialog = MutableStateFlow(false)
    val showQuizUpgradeDialog: StateFlow<Boolean> = _showQuizUpgradeDialog.asStateFlow()

    private val _quizLimitMessage = MutableStateFlow("")
    val quizLimitMessage: StateFlow<String> = _quizLimitMessage.asStateFlow()

    fun dismissQuizUpgradeDialog() {
        _showQuizUpgradeDialog.value = false
    }

    init {
        // Warm the cache for the default topic as soon as the ViewModel is created
        warmQuizCache(photographyTopics.first())
    }

    private fun warmQuizCache(topic: String) {
        viewModelScope.launch {
            _quizCacheWarming.value = true
            try {
                val token = authManager.getCurrentUser()?.accessToken ?: return@launch
                repository.generateQuiz(topic = topic, authToken = token)
            } catch (_: Exception) {
            } finally {
                _quizCacheWarming.value = false
            }
        }
    }

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
                    sessionId = sessionId,
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
        warmQuizCache(topic) // Pre-warm cache so Start Quiz is instant
    }

    fun startQuiz() {
        val topic = _selectedTopic.value
        _quizState.value = QuizUiState.Loading

        viewModelScope.launch {
            try {
                val token = authManager.getCurrentUser()?.accessToken
                    ?: throw Exception("Not authenticated")
                repository.generateQuiz(topic = topic, authToken = token).fold(
                    onSuccess = { _quizState.value = QuizUiState.Active(quiz = it) },
                    onFailure = { error ->
                        // ── Handle 429 quiz limit exceeded ────────────────────
                        // Parse the structured error body from the backend and
                        // show an upgrade dialog instead of a generic error.
                        val httpError = error as? HttpException
                        if (httpError?.code() == 429) {
                            try {
                                val body = httpError.response()?.errorBody()?.string()
                                val detail = JSONObject(body ?: "{}").optJSONObject("detail")
                                val msg = detail?.optString("message")
                                    ?: "You've reached your monthly quiz limit."
                                _quizLimitMessage.value = msg
                                _showQuizUpgradeDialog.value = true
                                _quizState.value = QuizUiState.Idle
                            } catch (_: Exception) {
                                // Fallback if body parsing fails
                                _quizLimitMessage.value = "You've reached your monthly quiz limit."
                                _showQuizUpgradeDialog.value = true
                                _quizState.value = QuizUiState.Idle
                            }
                        } else {
                            _quizState.value = QuizUiState.Error(
                                error.message ?: "Failed to generate quiz"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _quizState.value = QuizUiState.Error(e.message ?: "Not authenticated")
            }
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
