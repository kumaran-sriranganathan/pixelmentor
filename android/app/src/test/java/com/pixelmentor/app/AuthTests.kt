package com.pixelmentor.app

import com.pixelmentor.app.data.api.AuthInterceptor
import com.pixelmentor.app.data.api.TokenRefreshInterceptor
import com.pixelmentor.app.data.auth.AuthRepository
import com.pixelmentor.app.domain.model.AuthState
import com.pixelmentor.app.domain.model.AuthUser
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TokenRefreshInterceptorTest {

    private lateinit var authRepository: AuthRepository
    private lateinit var interceptor: TokenRefreshInterceptor
    private lateinit var chain: Interceptor.Chain

    private val mockUser = AuthUser(
        id = "user-123",
        email = "test@example.com",
        displayName = "Test User",
        accessToken = "fresh-token-456",
    )

    @Before
    fun setup() {
        authRepository = mockk()
        interceptor = TokenRefreshInterceptor(authRepository)
        chain = mockk()

        every { chain.request() } returns Request.Builder()
            .url("https://api.example.com/api/v1/lessons/")
            .build()
    }

    @Test
    fun `passes through non-401 responses unchanged`() {
        val response = buildResponse(200, """{"data": []}""")
        every { chain.proceed(any()) } returns response

        val result = interceptor.intercept(chain)

        assertEquals(200, result.code)
        verify(exactly = 1) { chain.proceed(any()) }
    }

    @Test
    fun `passes through 401 with token_invalid without refreshing`() {
        val response = buildResponse(401, """{"detail": {"error": "token_invalid", "message": "Token is invalid"}}""")
        every { chain.proceed(any()) } returns response

        val result = interceptor.intercept(chain)

        assertEquals(401, result.code)
        // No refresh attempted — only one proceed call
        verify(exactly = 1) { chain.proceed(any()) }
        coVerify(exactly = 0) { authRepository.refreshToken() }
    }

    @Test
    fun `retries with new token on token_expired`() = runTest {
        val expiredResponse = buildResponse(401, """{"detail": {"error": "token_expired", "message": "Token has expired"}}""")
        val successResponse = buildResponse(200, """{"data": []}""")

        every { chain.proceed(any()) } returnsMany listOf(expiredResponse, successResponse)
        coEvery { authRepository.refreshToken() } returns "fresh-token-456"

        val result = interceptor.intercept(chain)

        assertEquals(200, result.code)
        // Called twice — original request + retry with new token
        verify(exactly = 2) { chain.proceed(any()) }
        coVerify(exactly = 1) { authRepository.refreshToken() }
    }

    @Test
    fun `passes through 401 if silent refresh returns null`() = runTest {
        val expiredResponse = buildResponse(401, """{"detail": {"error": "token_expired", "message": "Token has expired"}}""")
        val unauthResponse = buildResponse(401, """{"detail": {"error": "token_missing"}}""")

        every { chain.proceed(any()) } returnsMany listOf(expiredResponse, unauthResponse)
        coEvery { authRepository.refreshToken() } returns null

        val result = interceptor.intercept(chain)

        assertEquals(401, result.code)
        coVerify(exactly = 1) { authRepository.refreshToken() }
    }

    @Test
    fun `retry request includes new Bearer token in header`() = runTest {
        val expiredResponse = buildResponse(401, """{"detail": {"error": "token_expired"}}""")
        val successResponse = buildResponse(200, """{}""")

        val capturedRequests = mutableListOf<Request>()
        every { chain.proceed(capture(capturedRequests)) } returnsMany listOf(expiredResponse, successResponse)
        coEvery { authRepository.refreshToken() } returns "brand-new-token"

        interceptor.intercept(chain)

        val retryRequest = capturedRequests[1]
        assertEquals("Bearer brand-new-token", retryRequest.header("Authorization"))
    }

    private fun buildResponse(code: Int, body: String): Response {
        return Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }
}

// ── AuthInterceptor tests ─────────────────────────────────────────────────────

class AuthInterceptorTest {

    private lateinit var authRepository: AuthRepository
    private lateinit var interceptor: AuthInterceptor
    private lateinit var chain: Interceptor.Chain

    @Before
    fun setup() {
        authRepository = mockk()
        interceptor = AuthInterceptor(authRepository)
        chain = mockk()

        every { chain.request() } returns Request.Builder()
            .url("https://api.example.com/api/v1/lessons/")
            .build()
        every { chain.proceed(any()) } returns mockk(relaxed = true)
    }

    @Test
    fun `attaches Bearer token when user is authenticated`() {
        every { authRepository.currentToken } returns "valid-token-123"

        val capturedRequest = slot<Request>()
        every { chain.proceed(capture(capturedRequest)) } returns mockk(relaxed = true)

        interceptor.intercept(chain)

        assertEquals("Bearer valid-token-123", capturedRequest.captured.header("Authorization"))
    }

    @Test
    fun `proceeds without auth header when no token available`() {
        every { authRepository.currentToken } returns null

        val capturedRequest = slot<Request>()
        every { chain.proceed(capture(capturedRequest)) } returns mockk(relaxed = true)

        interceptor.intercept(chain)

        assertNull(capturedRequest.captured.header("Authorization"))
    }
}

// ── AuthRepository tests ──────────────────────────────────────────────────────

class AuthRepositoryTest {

    private lateinit var msalAuthManager: com.pixelmentor.app.data.auth.MsalAuthManager
    private lateinit var authRepository: AuthRepository

    private val mockUser = AuthUser(
        id = "user-123",
        email = "test@example.com",
        displayName = "Test User",
        accessToken = "token-abc",
    )

    @Before
    fun setup() {
        msalAuthManager = mockk()
        authRepository = AuthRepository(msalAuthManager)
    }

    @Test
    fun `restoreSession sets Authenticated state when account exists`() = runTest {
        coEvery { msalAuthManager.getCurrentAccount() } returns mockUser

        authRepository.restoreSession()

        val state = authRepository.authState.value
        assertTrue(state is AuthState.Authenticated)
        assertEquals("user-123", (state as AuthState.Authenticated).user.id)
    }

    @Test
    fun `restoreSession sets Unauthenticated when no account`() = runTest {
        coEvery { msalAuthManager.getCurrentAccount() } returns null

        authRepository.restoreSession()

        assertTrue(authRepository.authState.value is AuthState.Unauthenticated)
    }

    @Test
    fun `refreshToken returns new token on success`() = runTest {
        val refreshedUser = mockUser.copy(accessToken = "new-token-xyz")
        coEvery { msalAuthManager.acquireTokenSilently() } returns refreshedUser

        val token = authRepository.refreshToken()

        assertEquals("new-token-xyz", token)
        assertTrue(authRepository.authState.value is AuthState.Authenticated)
    }

    @Test
    fun `refreshToken returns null and sets Unauthenticated on MsalUiRequiredException`() = runTest {
        coEvery { msalAuthManager.acquireTokenSilently() } throws
            com.microsoft.identity.client.exception.MsalUiRequiredException(
                com.microsoft.identity.client.exception.MsalUiRequiredException.NO_CURRENT_ACCOUNT,
                "Refresh token expired"
            )

        val token = authRepository.refreshToken()

        assertNull(token)
        assertTrue(authRepository.authState.value is AuthState.Unauthenticated)
    }

    @Test
    fun `signOut clears auth state`() = runTest {
        coEvery { msalAuthManager.signOut() } just Runs

        authRepository.signOut()

        assertTrue(authRepository.authState.value is AuthState.Unauthenticated)
    }
}
