package com.pixelmentor.app.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.pixelmentor.app.BuildConfig
import com.pixelmentor.app.domain.model.AuthUser
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.createSupabaseClient
import javax.inject.Inject
import javax.inject.Singleton

sealed class GoogleSignInResult {
    data object Success : GoogleSignInResult()
    data object Cancelled : GoogleSignInResult()
    data class Error(val message: String, val cause: Exception? = null) : GoogleSignInResult()
}

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

    /**
     * Performs native Google Sign-In using Android Credential Manager.
     * Gets a Google ID token, then exchanges it with Supabase via the IDToken provider.
     * No browser redirect needed — shows the native account picker bottom sheet.
     */
    suspend fun signInWithGoogle(context: Context): GoogleSignInResult {
        return try {
            val credentialManager = CredentialManager.create(context)

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false) // show all accounts, not just previously used
                .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(context, request)
            val googleCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
            val idToken = googleCredential.idToken

            // Exchange the Google ID token with Supabase
            client.auth.signInWith(IDToken) {
                provider = Google
                idToken = idToken
            }

            GoogleSignInResult.Success
        } catch (e: GetCredentialCancellationException) {
            GoogleSignInResult.Cancelled
        } catch (e: Exception) {
            GoogleSignInResult.Error(e.message ?: "Google sign-in failed", e)
        }
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
