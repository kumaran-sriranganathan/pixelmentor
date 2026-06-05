package com.pixelmentor.app.data.api

import com.pixelmentor.app.domain.model.Lesson
import com.pixelmentor.app.domain.model.Plan
import com.pixelmentor.app.domain.model.PhotoAnalysisRequest
import com.pixelmentor.app.domain.model.PhotoAnalysisResponse
import com.pixelmentor.app.domain.model.SkillLevel
import com.pixelmentor.app.domain.model.UserProfile
import com.pixelmentor.app.domain.model.QuizRequest
import com.pixelmentor.app.domain.model.QuizResponse
import retrofit2.http.Header
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.DELETE
import retrofit2.http.Path
import retrofit2.http.Query

// ── Retrofit service ──────────────────────────────────────────────────────────

interface PixelMentorApiService {

    @GET("api/v1/lessons")
    suspend fun getLessons(
        @Query("difficulty") difficulty: String? = null,
        @Query("category") category: String? = null,
        @Query("q") q: String? = null,
    ): LessonsResponseDto

    @GET("api/v1/lessons/{id}")
    suspend fun getLesson(@Path("id") id: String): LessonDetailDto

    @GET("api/v1/users/{userId}")
    suspend fun getUser(@Path("userId") userId: String): UserProfileDto

    @POST("api/v1/analyze/photo")
    suspend fun analyzePhoto(
        @Header("Authorization") authorization: String,
        @Body body: PhotoAnalysisRequest
    ): PhotoAnalysisResponse

    @POST("api/v1/tutor/quiz")
    suspend fun generateQuiz(
        @Header("Authorization") authorization: String,
        @Body body: QuizRequest
    ): QuizResponse

    @GET("api/v1/lessons/{id}/content")
    suspend fun getLessonContent(@Path("id") id: String): LessonContentDto

    @POST("api/v1/lessons/{id}/complete")
    suspend fun markLessonComplete(@Path("id") id: String): MarkCompleteResponseDto

    @GET("api/v1/lessons/completions")
    suspend fun getCompletions(): CompletionsDto

    @DELETE("api/v1/users/{userId}")
    suspend fun deleteAccount(@Path("userId") userId: String): DeleteAccountResponseDto

    @GET("api/v1/analyze/usage")
    suspend fun getPhotoUsage(): PhotoUsageDto
}

// ── DTOs ──────────────────────────────────────────────────────────────────────

data class LessonsResponseDto(
    val lessons: List<LessonDto>,
    val total_count: Int,
    val page: Int,
    val page_size: Int,
)

data class LessonDto(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val difficulty: String,
    val duration_minutes: Int,
    val is_pro: Boolean,
    val order: Int,
    val tags: List<String>,
) {
    fun toDomain() = Lesson(
        id = id,
        title = title,
        description = description,
        durationMinutes = duration_minutes,
        skillLevel = SkillLevel.from(difficulty),
        thumbnailUrl = "",
    )
}

data class LessonDetailDto(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val difficulty: String,
    val duration_minutes: Int,
    val is_pro: Boolean,
    val order: Int,
    val tags: List<String>,
    val content: String,
) {
    fun toDomain() = Lesson(
        id = id,
        title = title,
        description = description,
        durationMinutes = duration_minutes,
        skillLevel = SkillLevel.from(difficulty),
        thumbnailUrl = "",
    )
}

data class UserProfileDto(
    val user_id: String,
    val display_name: String,
    val skill_level: String,
    val photos_analyzed: Int,
    val lessons_completed: Int,
    val streak_days: Int,
    val plan: String,
) {
    fun toDomain() = UserProfile(
        userId = user_id,
        displayName = display_name,
        skillLevel = SkillLevel.from(skill_level),
        photosAnalyzed = photos_analyzed,
        photosAnalyzedThisMonth = 0,
        photosAllTime = photos_analyzed,
        lessonsCompleted = lessons_completed,
        streakDays = streak_days,
        plan = Plan.from(plan),
    )
}

data class LessonContentDto(
    val lesson_id: String,
    val content: String,
)

data class MarkCompleteResponseDto(
    val lesson_id: String,
    val completed: Boolean,
)

data class CompletionsDto(
    val completed_lesson_ids: List<String>,
)

data class DeleteAccountResponseDto(
    val message: String,
)

data class ChatRequest(
    val message: String,
    val session_id: String,
    val topic: String? = null,
)

data class PhotoUsageDto(
    val photos_used_this_month: Int,
    val photos_limit: Int,
    val photos_remaining: Int,
    val plan: String,
)
