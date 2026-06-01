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
        // Use runBlocking to safely fetch token on OkHttp's IO thread
        val token = runBlocking { authRepository.getValidToken() }
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
            "token_invalid", "token_missing" -> {
                // Not recoverable by refresh — notify immediately and pass through
                runBlocking { authRepository.notifyForceLogout() }
                response
            }
            // Unknown 401 — pass through and let the ViewModel handle it
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
            // ── Refresh failed — session is unrecoverable ─────────────────────
            // notifyForceLogout() has already been called inside refreshToken(),
            // which sets authState = Unauthenticated and emits the forceLogout
            // signal. MainActivity will navigate to Login with a friendly message.
            //
            // We return a synthetic 401 here rather than retrying without a token.
            // Retrying unauthenticated would hammer the backend on every queued
            // request, eventually triggering a real 429 rate-limit — which is
            // exactly the confusing error users were seeing after sign-out.
            okhttp3.Response.Builder()
                .request(chain.request())
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(401)
                .message("Session expired")
                .body(okhttp3.ResponseBody.create(null, ""))
                .build()
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
