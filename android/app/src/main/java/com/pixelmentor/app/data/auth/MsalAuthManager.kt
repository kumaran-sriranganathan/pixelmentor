package com.pixelmentor.app.data.auth

import android.app.Activity
import android.content.Context
import com.microsoft.identity.client.*
import com.microsoft.identity.client.exception.MsalException
import com.microsoft.identity.client.exception.MsalUiRequiredException
import com.pixelmentor.app.BuildConfig
import com.pixelmentor.app.R
import com.pixelmentor.app.domain.model.AuthUser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Wraps MSAL's callback-based API with coroutines.
 *
 * Scopes: we request the PixelMentor API scope so the access token
 * is valid for our backend (audience = entra_api_client_id).
 */
@Singleton
class MsalAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        // API scope — matches the scope exposed by the PixelMentor API app registration
        private const val API_SCOPE =
            "https://pixelmentor.onmicrosoft.com/pixelmentor-api/access_as_user"

        private val SCOPES = arrayOf(API_SCOPE)
    }

    @Volatile
    private var msalApp: ISingleAccountPublicClientApplication? = null

    /**
     * Initialise MSAL. Called once from the Hilt module at app start.
     * Suspends until the client is ready.
     */
    suspend fun initialize(): Unit = suspendCancellableCoroutine { cont ->
        PublicClientApplication.createSingleAccountPublicClientApplication(
            context,
            R.raw.auth_config,
            object : IPublicClientApplication.ISingleAccountApplicationCreatedListener {
                override fun onCreated(application: ISingleAccountPublicClientApplication) {
                    msalApp = application
                    cont.resume(Unit)
                }

                override fun onError(exception: MsalException) {
                    cont.resumeWithException(exception)
                }
            }
        )
    }

    /**
     * Interactive sign-in — opens the Entra External ID browser flow.
     * User chooses Google or Email/Password on the Entra-hosted page.
     */
    suspend fun signIn(activity: Activity): AuthUser =
        suspendCancellableCoroutine { cont ->
            requireApp().signIn(
                activity,
                null,
                SCOPES,
                object : AuthenticationCallback {
                    override fun onSuccess(result: IAuthenticationResult) {
                        cont.resume(result.toAuthUser())
                    }

                    override fun onError(exception: MsalException) {
                        cont.resumeWithException(exception)
                    }

                    override fun onCancel() {
                        cont.resumeWithException(
                            MsalException("User cancelled sign-in")
                        )
                    }
                }
            )
        }

    /**
     * Silent token acquisition — no UI shown.
     * Call this when the backend returns error=token_expired.
     * Throws MsalUiRequiredException if the refresh token is also expired
     * (caller should then call signIn() for interactive re-login).
     */
    suspend fun acquireTokenSilently(): AuthUser =
        suspendCancellableCoroutine { cont ->
            val account = requireApp().currentAccount?.currentAccount
                ?: return@suspendCancellableCoroutine cont.resumeWithException(
                    MsalUiRequiredException(
                        MsalUiRequiredException.NO_CURRENT_ACCOUNT,
                        "No current account for silent refresh"
                    )
                )

            requireApp().acquireTokenSilentAsync(
                SCOPES,
                account,
                object : SilentAuthenticationCallback {
                    override fun onSuccess(result: IAuthenticationResult) {
                        cont.resume(result.toAuthUser())
                    }

                    override fun onError(exception: MsalException) {
                        cont.resumeWithException(exception)
                    }
                }
            )
        }

    /**
     * Returns the current account's cached access token, or null if
     * no user is signed in.
     */
    suspend fun getCurrentAccount(): AuthUser? =
        suspendCancellableCoroutine { cont ->
            requireApp().getCurrentAccountAsync(
                object : ISingleAccountPublicClientApplication.CurrentAccountCallback {
                    override fun onAccountLoaded(activeAccount: IAccount?) {
                        if (activeAccount == null) {
                            cont.resume(null)
                            return
                        }
                        // Attempt a silent token fetch to get a fresh access token
                        requireApp().acquireTokenSilentAsync(
                            SCOPES,
                            activeAccount,
                            object : SilentAuthenticationCallback {
                                override fun onSuccess(result: IAuthenticationResult) {
                                    cont.resume(result.toAuthUser())
                                }

                                override fun onError(exception: MsalException) {
                                    // Couldn't get a token silently — treat as unauthenticated
                                    cont.resume(null)
                                }
                            }
                        )
                    }

                    override fun onAccountChanged(priorAccount: IAccount?, currentAccount: IAccount?) {
                        cont.resume(null)
                    }

                    override fun onError(exception: MsalException) {
                        cont.resume(null)
                    }
                }
            )
        }

    suspend fun signOut(): Unit = suspendCancellableCoroutine { cont ->
        requireApp().signOut(object : ISingleAccountPublicClientApplication.SignOutCallback {
            override fun onSignOut() {
                cont.resume(Unit)
            }

            override fun onError(exception: MsalException) {
                cont.resumeWithException(exception)
            }
        })
    }

    private fun requireApp() = msalApp
        ?: error("MsalAuthManager not initialised — call initialize() first")

    private fun IAuthenticationResult.toAuthUser() = AuthUser(
        id = account.id,
        email = account.username,
        displayName = account.claims?.get("name") as? String,
        accessToken = accessToken,
    )
}
