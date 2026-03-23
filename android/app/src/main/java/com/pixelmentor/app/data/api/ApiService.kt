package com.pixelmentor.app.data.api

import com.pixelmentor.app.domain.model.Lesson
import com.pixelmentor.app.domain.model.Plan
import com.pixelmentor.app.domain.model.SkillLevel
import com.pixelmentor.app.domain.model.UserProfile
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// ── Retrofit service ──────────────────────────────────────────────────────────

interface PixelMentorApiService {

    @GET("api/v1/lessons/")
    suspend fun getLessons(
        @Query("skill_level") skillLevel: String? = null,
    ): List<LessonDto>

    @GET("api/v1/lessons/{id}")
    suspend fun getLesson(@Path("id") id: String): LessonDto

    @GET("api/v1/users/{userId}")
    suspend fun getUser(@Path("userId") userId: String): UserProfileDto
}

// ── DTOs ──────────────────────────────────────────────────────────────────────

data class LessonDto(
    val id: String,
    val title: String,
    val description: String,
    val duration_minutes: Int,
    val skill_level: String,
    val thumbnail_url: String = "",
) {
    fun toDomain() = Lesson(
        id = id,
        title = title,
        description = description,
        durationMinutes = duration_minutes,
        skillLevel = SkillLevel.from(skill_level),
        thumbnailUrl = thumbnail_url,
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
        lessonsCompleted = lessons_completed,
        streakDays = streak_days,
        plan = Plan.from(plan),
    )
}
