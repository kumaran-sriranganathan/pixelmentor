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
        topic: String? = null,
        authToken: String
    ): Flow<String> = flow {
        val body = JSONObject().apply {
            put("message", message)
            topic?.let { put("topic", it) }
        }.toString()

        val request = Request.Builder()
            .url("${BASE_URL}api/v1/tutor/chat")
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $authToken")
            .header("Accept", "text/event-stream")
            .build()

        val response = okHttpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("Chat failed: ${response.code}")
        }

        response.body?.source()?.let { source ->
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
        numQuestions: Int = 5
    ): Result<QuizResponse> = try {
        val response = apiService.generateQuiz(
            QuizRequest(
                topic = topic,
                difficulty = difficulty,
                numQuestions = numQuestions
            )
        )
        Result.success(response)
    } catch (e: Exception) {
        Result.failure(e)
    }

    companion object {
        // Should match your Railway URL — injected via BuildConfig in a real setup
        private const val BASE_URL = "https://pixelmentor-production.up.railway.app/"
    }
}
