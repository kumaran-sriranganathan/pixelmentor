package com.pixelmentor.app.data.repository

import com.pixelmentor.app.data.api.PixelMentorApiService
import com.pixelmentor.app.domain.model.QuizRequest
import com.pixelmentor.app.domain.model.QuizResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TutorRepository @Inject constructor(
    private val apiService: PixelMentorApiService,
    private val okHttpClient: OkHttpClient
) {

    /**
     * Streams the AI tutor response as a Flow of text chunks via SSE.
     */
    fun streamChat(
        message: String,
        sessionId: String,
        topic: String? = null,
        authToken: String
    ): Flow<String> = flow {
        val body = JSONObject().apply {
            put("message", message)
            put("session_id", sessionId)
            topic?.let { put("topic", it) }
        }.toString()

        val request = Request.Builder()
            .url("${BASE_URL}api/v1/tutor/chat")
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $authToken")
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")   // ensures a fresh SSE stream each request
            .header("Connection", "keep-alive")     // prevents stale connection reuse
            .build()

        val response = okHttpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("Chat failed: ${response.code}")
        }

        // .use{} guarantees the response body is closed after the flow completes,
        // preventing the previous connection's buffer leaking into the next request.
        response.body?.use { responseBody ->
            val source = responseBody.source()
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                when {
                    line.startsWith("data: ") -> {
                        val data = line.removePrefix("data: ").trim()
                        if (data == "[DONE]") return@flow
                        try {
                            val json = JSONObject(data)
                            val chunk = json.optString("content", "")
                                .ifEmpty { json.optString("text", "") }
                            if (chunk.isNotEmpty()) emit(chunk)
                        } catch (_: Exception) {
                            // Non-JSON data line — skip
                        }
                    }
                    line == "" -> { /* skip empty lines between SSE events */ }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Generates a quiz on the given topic.
     */
    suspend fun generateQuiz(
        topic: String,
        difficulty: String = "intermediate",
        numQuestions: Int = 5,
        authToken: String
    ): Result<QuizResponse> = try {
        val response = apiService.generateQuiz(
            authorization = "Bearer $authToken",
            body = QuizRequest(
                topic = topic,
                difficulty = difficulty,
                numQuestions = numQuestions
            )
        )
        Result.success(response)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Records that the user completed a quiz (reached the results screen).
     * Called from TutorViewModel when the last question is answered.
     */
    suspend fun recordQuizCompletion(topic: String, authToken: String): Result<Unit> = try {
        apiService.recordQuizCompletion(authorization = "Bearer $authToken")
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    companion object {
        // Should match your Railway URL — injected via BuildConfig in a real setup
        private const val BASE_URL = "https://pixelmentor-production.up.railway.app/"
    }
}