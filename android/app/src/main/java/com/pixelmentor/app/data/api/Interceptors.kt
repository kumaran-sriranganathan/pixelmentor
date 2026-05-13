package com.pixelmentor.app.data.api

import com.google.gson.Gson
import com.pixelmentor.app.data.auth.AuthRepository
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * OkHttp interceptor chain:
 *
 * 1. AuthInterceptor    — attaches Bearer token to every request
 * 2. TokenRefreshInterceptor — if response is 401 with error=token_expired,
 *    silently refreshes the token and retries once.
 *    If refresh fails (refresh token expired), passes 401 through so the
 *    app can redirect to login.
 */

// ── 1. Auth interceptor ───────────────────────────────────────────────────────

class AuthInterceptor(
    private val authRepository: AuthRepository,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = authRepository.currentToken
        val request = if (token != null) {
            chain.request().withBearerToken(token)
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}

// ── 2. Token refresh interceptor ─────────────────────────────────────────────

class TokenRefreshInterceptor(
    private val authRepository: AuthRepository,
) : Interceptor {

    private val gson = Gson()

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        if (response.code != 401) return response

        // Parse the error code from the backend response body
        val errorCode = parseErrorCode(response)

        return when (errorCode) {
            "token_expired" -> {
                // Close the original response before retrying — OkHttp requires this
                response.close()
                handleTokenExpired(chain)
            }
            // token_invalid / token_missing — not recoverable by refresh, pass through
            else -> response
        }
    }

    private fun handleTokenExpired(chain: Interceptor.Chain): Response {
        // runBlocking is acceptable here — we're already on OkHttp's IO thread
        val newToken = runBlocking { authRepository.refreshToken() }

        return if (newToken != null) {
            // Retry the original request with the fresh token
            val retryRequest = chain.request().withBearerToken(newToken)
            chain.proceed(retryRequest)
        } else {
            // Silent refresh failed — proceed without token so the 401
            // propagates to the ViewModel which redirects to login
            chain.proceed(chain.request())
        }
    }

    private fun parseErrorCode(response: Response): String? {
        return try {
            val bodyString = response.peekBody(1024).string()
            val parsed = gson.fromJson(bodyString, ErrorResponse::class.java)
            parsed?.detail?.error
        } catch (e: Exception) {
            null
        }
    }

    private data class ErrorResponse(val detail: ErrorDetail?)
    private data class ErrorDetail(val error: String?, val message: String?)
}

// ── Extension ─────────────────────────────────────────────────────────────────

fun Request.withBearerToken(token: String): Request =
    newBuilder()
        .header("Authorization", "Bearer $token")
        .build()
