package com.pixelmentor.app.data.repository

import com.pixelmentor.app.data.api.PixelMentorApiService
import com.pixelmentor.app.domain.model.AppException
import com.pixelmentor.app.domain.model.Lesson
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
            api.getLessons(skillLevel?.value).map { it.toDomain() }
        }
    }

    suspend fun getLesson(id: String): Result<Lesson> {
        return safeApiCall {
            api.getLesson(id).toDomain()
        }
    }
}

// ── Safe call wrapper ─────────────────────────────────────────────────────────

suspend fun <T> safeApiCall(call: suspend () -> T): Result<T> {
    return try {
        Result.Success(call())
    } catch (e: HttpException) {
        val exception = when (e.code()) {
            401 -> {
                // TokenRefreshInterceptor already attempted a silent refresh.
                // If we still get 401 here, the token is invalid or the user
                // must re-login — surface as Unauthorized.
                AppException.Unauthorized()
            }
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
