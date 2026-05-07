package com.pixelmentor.app.data.auth

import com.pixelmentor.app.BuildConfig
import com.pixelmentor.app.domain.model.AuthUser
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.compose.auth.ComposeAuth
import io.github.jan.supabase.compose.auth.googleNativeLogin
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
        install(ComposeAuth) {
            // Uses Credential Manager — no browser redirect needed
            googleNativeLogin(serverClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID)
        }
    }

    suspend fun signInWithEmail(email: String, password: String): AuthUser {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        return getCurrentUser() ?: error("Sign in succeeded but no user found")
    }

    // Google Sign-In is handled directly in the composable via ComposeAuth.
    // This function is kept for compatibility but should not be called directly —
    // use the rememberSignInWithGoogle() hook in LoginScreen instead.
    suspend fun signInWithGoogle() {
        error("Use ComposeAuth rememberSignInWithGoogle() in the UI layer instead")
    }

    suspend fun signUp(email: String, password: String): AuthUser {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        return getCurrentUser() ?: error("Sign up succeeded but no user found")
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
        client.auth.signOut()
    }
}
