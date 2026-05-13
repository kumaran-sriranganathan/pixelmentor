package com.pixelmentor.app.data.repository

import com.pixelmentor.app.data.api.PixelMentorApiService
import com.pixelmentor.app.domain.model.UserProfile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val apiService: PixelMentorApiService
) {
    suspend fun getProfile(userId: String): Result<UserProfile> = try {
        val dto = apiService.getUser(userId)
        Result.success(dto.toDomain())
    } catch (e: Exception) {
        Result.failure(e)
    }
}
