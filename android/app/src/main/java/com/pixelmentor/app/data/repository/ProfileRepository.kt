package com.pixelmentor.app.data.repository

import com.pixelmentor.app.data.api.PixelMentorApiService
import com.pixelmentor.app.domain.model.UserProfile
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

// Note: NOT @Singleton — a fresh instance is used per ViewModel injection
// so there is no risk of one user's profile being shown to another.
class ProfileRepository @Inject constructor(
    private val apiService: PixelMentorApiService
) {
    suspend fun getProfile(userId: String): Result<UserProfile> = try {
        coroutineScope {
            // Fetch profile, photo usage, and quiz usage all in parallel
            val profileDeferred = async { apiService.getUser(userId) }
            val usageDeferred = async {
                try { apiService.getPhotoUsage() } catch (_: Exception) { null }
            }
            val quizUsageDeferred = async {
                try { apiService.getQuizUsage() } catch (_: Exception) { null }
            }

            val dto = profileDeferred.await()
            val usage = usageDeferred.await()
            val quizUsage = quizUsageDeferred.await()

            // toDomain() called once and cached — avoids redundant object creation
            val profile = dto.toDomain()
            val merged = profile.copy(
                photosAnalyzedThisMonth = usage?.photos_used_this_month ?: 0,
                photosAllTime = profile.photosAnalyzed,
                quizzesUsedThisMonth = quizUsage?.quizzes_used_this_month ?: 0,
                quizzesCompletedThisMonth = quizUsage?.quizzes_completed_this_month ?: 0,
                quizLimit = quizUsage?.quiz_limit ?: 5,
            )
            Result.success(merged)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun deleteAccount(userId: String): Result<Unit> = try {
        apiService.deleteAccount(userId)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}