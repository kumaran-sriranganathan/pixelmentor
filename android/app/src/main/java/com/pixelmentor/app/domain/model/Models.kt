package com.pixelmentor.app.domain.model

// ── Auth ──────────────────────────────────────────────────────────────────────

sealed class AuthState {
    data object Loading : AuthState()
    data object Unauthenticated : AuthState()
    data class Authenticated(val user: AuthUser) : AuthState()
}

data class AuthUser(
    val id: String,
    val email: String?,
    val displayName: String?,
    val accessToken: String,
)

// ── Lesson ────────────────────────────────────────────────────────────────────

data class Lesson(
    val id: String,
    val title: String,
    val description: String,
    val durationMinutes: Int,
    val skillLevel: SkillLevel,
    val thumbnailUrl: String = "",
)

enum class SkillLevel(val value: String) {
    BEGINNER("beginner"),
    INTERMEDIATE("intermediate"),
    ADVANCED("advanced");

    companion object {
        fun from(value: String) = entries.firstOrNull { it.value == value } ?: BEGINNER
    }
}

// ── User ──────────────────────────────────────────────────────────────────────

data class UserProfile(
    val userId: String,
    val displayName: String,
    val skillLevel: SkillLevel,
    val photosAnalyzed: Int,
    val lessonsCompleted: Int,
    val streakDays: Int,
    val plan: Plan,
)

enum class Plan(val value: String, val label: String, val emoji: String) {
    FREE("free", "Free", ""),
    PRO("pro", "Pro", "⚡"),
    PREMIUM("premium", "Premium", "👑");

    companion object {
        fun from(value: String) = entries.firstOrNull { it.value == value } ?: FREE
    }
}

// ── Result wrapper ────────────────────────────────────────────────────────────

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: AppException) : Result<Nothing>()
}

sealed class AppException(message: String) : Exception(message) {
    data class TokenExpired(override val message: String = "Token expired") : AppException(message)
    data class TokenInvalid(override val message: String = "Token invalid") : AppException(message)
    data class Unauthorized(override val message: String = "Unauthorized") : AppException(message)
    data class NetworkError(override val message: String) : AppException(message)
    data class ServerError(val code: Int, override val message: String) : AppException(message)
    data class Unknown(override val message: String = "Unknown error") : AppException(message)
}

// ── Profile UI State ──────────────────────────────────────────────────────────

sealed class ProfileUiState {
    object Loading : ProfileUiState()
    data class Success(val profile: UserProfile) : ProfileUiState()
    data class Error(val message: String) : ProfileUiState()
}
