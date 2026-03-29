package com.pixelmentor.app.data.auth

import android.app.Activity
import android.content.Context
import com.microsoft.identity.client.*
import com.microsoft.identity.client.exception.MsalException
import com.microsoft.identity.client.exception.MsalClientException
import com.microsoft.identity.client.exception.MsalUiRequiredException
import com.pixelmentor.app.R
import com.pixelmentor.app.domain.model.AuthUser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class MsalAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val API_SCOPE =
            "api://2b0185be-147c-4751-be94-4b6905f4efa3/access_as_user"
        private val SCOPES = arrayOf(API_SCOPE)

        // Entra External ID (CIAM) authority
        private const val AUTHORITY = "https://pixelmentor.ciamlogin.com/pixelmentor.onmicrosoft.com/"
    }

    @Volatile
    private var msalApp: ISingleAccountPublicClientApplication? = null

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
                        cont.resumeWithException(MsalClientException("user_cancelled", "User cancelled sign-in"))
                    }
                }
            )
        }

    /**
     * Silent token acquisition using the account identifier string.
     * MSAL 5.x acquireTokenSilentAsync takes (scopes, accountIdentifier, authority).
     */
    suspend fun acquireTokenSilently(): AuthUser =
        suspendCancellableCoroutine { cont ->
            val accountId = requireApp().currentAccount?.currentAccount?.id
                ?: return@suspendCancellableCoroutine cont.resumeWithException(
                    MsalUiRequiredException("NO_CURRENT_ACCOUNT", "No current account")
                )

            val params = AcquireTokenSilentParameters.Builder()
                .forAccount(requireApp().currentAccount?.currentAccount)
                .fromAuthority(AUTHORITY)
                .withScopes(SCOPES.toList())
                .withCallback(object : SilentAuthenticationCallback {
                    override fun onSuccess(result: IAuthenticationResult) {
                        cont.resume(result.toAuthUser())
                    }
                    override fun onError(exception: MsalException) {
                        cont.resumeWithException(exception)
                    }
                })
                .build()

            requireApp().acquireTokenSilentAsync(params)
        }

    suspend fun getCurrentAccount(): AuthUser? =
        suspendCancellableCoroutine { cont ->
            requireApp().getCurrentAccountAsync(
                object : ISingleAccountPublicClientApplication.CurrentAccountCallback {
                    override fun onAccountLoaded(activeAccount: IAccount?) {
                        if (activeAccount == null) {
                            cont.resume(null)
                            return
                        }
                        val params = AcquireTokenSilentParameters.Builder()
                            .forAccount(activeAccount)
                            .fromAuthority(AUTHORITY)
                            .withScopes(SCOPES.toList())
                            .withCallback(object : SilentAuthenticationCallback {
                                override fun onSuccess(result: IAuthenticationResult) {
                                    cont.resume(result.toAuthUser())
                                }
                                override fun onError(exception: MsalException) {
                                    cont.resume(null)
                                }
                            })
                            .build()
                        requireApp().acquireTokenSilentAsync(params)
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
            override fun onSignOut() { cont.resume(Unit) }
            override fun onError(exception: MsalException) { cont.resumeWithException(exception) }
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
