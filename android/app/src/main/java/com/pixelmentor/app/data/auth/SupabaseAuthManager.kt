package com.pixelmentor.app.data.auth

import com.pixelmentor.app.BuildConfig
import com.pixelmentor.app.domain.model.AuthUser
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseAuthManager @Inject constructor() {

    val client: SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
    ) {
        install(Auth)
    }

    suspend fun signInWithEmail(email: String, password: String): AuthUser {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        return getCurrentUser() ?: error("Sign in succeeded but no user found")
    }

    suspend fun signUp(email: String, password: String): AuthUser {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        return getCurrentUser() ?: error("Sign up succeeded but no user found")
    }

    /**
     * Sends a password reset email. Supabase emails a link that deep-links back
     * to io.supabase.pixelmentor://login-callback with a recovery token.
     */
    suspend fun sendPasswordResetEmail(email: String) {
        client.auth.resetPasswordForEmail(
            email = email,
            redirectUrl = "io.supabase.pixelmentor://login-callback",
        )
    }

    /**
     * Exchanges the recovery token from the deep link URL for a valid session.
     * Must be called BEFORE updatePassword() when handling a password reset deep link.
     *
     * The deep link arrives as:
     *   io.supabase.pixelmentor://login-callback#access_token=xxx&refresh_token=yyy&type=recovery
     *
     * We extract the access_token and refresh_token from the fragment and use them
     * to establish a session so updatePassword() has a valid JWT with a sub claim.
     */
    suspend fun handlePasswordResetDeepLink(deepLinkUrl: String) {
        try {
            // Parse fragment params from the deep link URL
            // Supabase sends tokens in the URL fragment (#) not query params (?)
            val fragment = deepLinkUrl.substringAfter("#", "")
            if (fragment.isEmpty()) return

            val params = fragment.split("&").associate {
                val parts = it.split("=", limit = 2)
                parts[0] to (parts.getOrNull(1) ?: "")
            }

            val accessToken = params["access_token"] ?: return
            val refreshToken = params["refresh_token"] ?: return
            val tokenType = params["token_type"] ?: "bearer"

            // Set the session directly using the tokens from the deep link
            client.auth.importSession(
                io.github.jan.supabase.auth.user.UserSession(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresIn = params["expires_in"]?.toLongOrNull() ?: 3600,
                    tokenType = tokenType,
                    user = null,
                )
            )
        } catch (e: Exception) {
            // Log but don't crash — updatePassword will fail with a clear error
            android.util.Log.e("SupabaseAuthManager", "Failed to handle reset deep link: $e")
        }
    }

    /**
     * Updates the current user's password. Called after the user taps the reset
     * link in the email and is returned to the app with an active recovery session.
     * Ensure handlePasswordResetDeepLink() has been called first.
     */
    suspend fun updatePassword(newPassword: String) {
        client.auth.updateUser {
            password = newPassword
        }
    }

    suspend fun getCurrentUser(): AuthUser? {
        val session = client.auth.currentSessionOrNull() ?: return null
        val user = client.auth.currentUserOrNull() ?: return null
        return AuthUser(
            id = user.id,
            email = user.email ?: "",
            displayName = user.userMetadata?.get("full_name")?.toString()?.trim('"'),
            accessToken = session.accessToken,
        )
    }

    suspend fun refreshSession(): AuthUser? {
        return try {
            client.auth.refreshCurrentSession()
            getCurrentUser()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun signOut() {
        // ── Local-only sign out ───────────────────────────────────────────────
        // We do NOT call client.auth.signOut() because it makes a blocking
        // network call to Supabase that hangs indefinitely when the connection
        // is slow — leaving the user stuck on the profile screen forever.
        //
        // Security: This is safe. clearSession() wipes the access token and
        // refresh token from device memory instantly. Even if someone captured
        // the old access token, it expires server-side within 1 hour (Supabase
        // default JWT expiry). Your backend validates the JWT signature on every
        // request and will reject it after expiry regardless.
        //
        // The server-side refresh token remains technically valid for up to 7 days,
        // but since we're clearing it from the device here, there is nothing left
        // on the phone that can use it. The security trade-off is acceptable for
        // a consumer photography app.
        client.auth.clearSession()
    }
}
