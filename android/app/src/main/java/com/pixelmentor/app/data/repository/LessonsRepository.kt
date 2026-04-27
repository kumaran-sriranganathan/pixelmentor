package com.pixelmentor.app.data.repository

import com.pixelmentor.app.data.api.PixelMentorApiService
import com.pixelmentor.app.domain.model.AppException
import com.pixelmentor.app.domain.model.Lesson
import com.pixelmentor.app.domain.model.LessonDetail
import com.pixelmentor.app.domain.model.Result
import com.pixelmentor.app.domain.model.SkillLevel
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LessonsRepository @Inject constructor(
    private val api: PixelMentorApiService,
) {
    suspend fun getLessons(skillLevel: SkillLevel? = null): Result<List<Lesson>> {
        return safeApiCall {
            api.getLessons(difficulty = skillLevel?.value).lessons.map { it.toDomain() }
        }
    }

    suspend fun getLesson(id: String): Result<LessonDetail> {
        return safeApiCall {
            val dto = api.getLesson(id)
            LessonDetail(
                id = dto.id,
                title = dto.title,
                description = dto.description,
                category = dto.category,
                skillLevel = SkillLevel.from(dto.difficulty),
                durationMinutes = dto.duration_minutes,
                isPro = dto.is_pro,
                tags = dto.tags,
                content = dto.content,
            )
        }
    }
}

// ── Safe call wrapper ─────────────────────────────────────────────────────────

suspend fun <T> safeApiCall(call: suspend () -> T): Result<T> {
    return try {
        Result.Success(call())
    } catch (e: HttpException) {
        val exception = when (e.code()) {
            401 -> AppException.Unauthorized()
            in 500..599 -> AppException.ServerError(e.code(), e.message())
            else -> AppException.Unknown(e.message())
        }
        Result.Error(exception)
    } catch (e: IOException) {
        Result.Error(AppException.NetworkError(e.message ?: "Network error"))
    } catch (e: Exception) {
        Result.Error(AppException.Unknown(e.message ?: "Unknown error"))
    }
}
