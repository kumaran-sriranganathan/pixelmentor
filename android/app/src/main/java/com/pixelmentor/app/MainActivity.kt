package com.pixelmentor.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pixelmentor.app.ui.auth.ForgotPasswordScreen
import com.pixelmentor.app.ui.auth.LoginScreen
import com.pixelmentor.app.ui.auth.ResetPasswordScreen
import com.pixelmentor.app.ui.lessons.LessonsScreen
import com.pixelmentor.app.ui.lessons.LessonDetailScreen
import com.pixelmentor.app.ui.tutor.TutorScreen
import com.pixelmentor.app.ui.profile.ProfileScreen
import com.pixelmentor.app.ui.upgrade.UpgradeScreen
import com.pixelmentor.app.ui.theme.PixelMentorTheme
import com.pixelmentor.app.ui.navigation.AnalysisRoutes
import com.pixelmentor.app.ui.navigation.PixelMentorBottomBar
import com.pixelmentor.app.ui.navigation.analysisGraph
import dagger.hilt.android.AndroidEntryPoint
import com.pixelmentor.app.data.auth.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import javax.inject.Inject

// ── Navigation routes ─────────────────────────────────────────────────────────

object Routes {
    const val LOGIN = "login"
    const val LESSONS = "lessons"
    const val TUTOR = "tutor"
    const val PROFILE = "profile"
    const val UPGRADE = "upgrade"
    const val FORGOT_PASSWORD = "forgot_password"
    const val RESET_PASSWORD = "reset_password"
    const val LESSON_DETAIL = "lessons/{lessonId}"

    fun lessonDetail(lessonId: String) = "lessons/$lessonId"
}

// ── MainActivity ──────────────────────────────────────────────────────────────

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // If this Activity was launched by a password reset deep link,
        // exchange the tokens from the URL fragment for a valid session
        // BEFORE the UI renders — otherwise updatePassword() fails with
        // "missing sub claim" because it runs against the anon key.
        intent?.data?.let { uri ->
            if (uri.scheme == "io.supabase.pixelmentor") {
                val fullUrl = uri.toString()
                if (fullUrl.contains("type=recovery") || fullUrl.contains("access_token=")) {
                    CoroutineScope(Dispatchers.IO).launch {
                        authRepository.handlePasswordResetDeepLink(fullUrl)
                    }
                }
            }
        }

        setContent {
            PixelMentorTheme {
                // Determine start route based on deep link type
                val deepLinkData = intent?.data
                val startRoute = when {
                    deepLinkData?.scheme == "io.supabase.pixelmentor" -> {
                        val type = deepLinkData.getQueryParameter("type")
                        when (type) {
                            // Email verified — send to login so user can sign in
                            "signup" -> Routes.LOGIN
                            // Password reset — send to reset password screen
                            "recovery" -> Routes.RESET_PASSWORD
                            // Any other deep link — default to reset password
                            else -> Routes.RESET_PASSWORD
                        }
                    }
                    else -> Routes.LOGIN
                }
                PixelMentorNavHost(
                    startRoute = startRoute,
                    authRepository = authRepository,
                )
            }
        }
    }

    // Handle deep-link when app is already running in the background
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Handle recovery deep link session exchange when app is already open
        intent.data?.let { uri ->
            if (uri.scheme == "io.supabase.pixelmentor") {
                val fullUrl = uri.toString()
                if (fullUrl.contains("type=recovery") || fullUrl.contains("access_token=")) {
                    CoroutineScope(Dispatchers.IO).launch {
                        authRepository.handlePasswordResetDeepLink(fullUrl)
                    }
                }
            }
        }
        recreate()
    }
}

@Composable
private fun PixelMentorNavHost(
    startRoute: String = Routes.LOGIN,
    authRepository: AuthRepository,
) {
    val navController = rememberNavController()

    // ── Global force-logout observer ──────────────────────────────────────────
    // When TokenRefreshInterceptor exhausts retries on a 401, it calls
    // authRepository.notifyForceLogout(). We catch that here — the single place
    // in the app that owns the nav controller — and redirect to Login with a
    // user-friendly message regardless of which screen the user is on.
    var sessionExpiredMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        authRepository.forceLogout.collect { reason ->
            sessionExpiredMessage = reason
            navController.navigate(Routes.LOGIN) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    Scaffold(
        bottomBar = { PixelMentorBottomBar(navController = navController) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startRoute,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.LOGIN) {
                LoginScreen(
                    onAuthenticated = {
                        sessionExpiredMessage = null
                        navController.navigate(Routes.LESSONS) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    },
                    onForgotPassword = {
                        navController.navigate(Routes.FORGOT_PASSWORD)
                    },
                    sessionExpiredMessage = sessionExpiredMessage,
                )
            }

            composable(Routes.FORGOT_PASSWORD) {
                ForgotPasswordScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.RESET_PASSWORD) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val activity = context as? android.app.Activity
                ResetPasswordScreen(
                    onPasswordReset = {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(Routes.RESET_PASSWORD) { inclusive = true }
                        }
                    },
                    // Pass the full deep link URL so ResetPasswordScreen can
                    // exchange the recovery tokens for a valid session
                    deepLinkUrl = activity?.intent?.data?.toString()
                )
            }

            composable(Routes.LESSONS) {
                LessonsScreen(
                    onSignOut = {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(Routes.LESSONS) { inclusive = true }
                        }
                    },
                    onAnalyzePhoto = { navController.navigate(AnalysisRoutes.PICKER) },
                    onLessonClick = { lessonId ->
                        navController.navigate(Routes.lessonDetail(lessonId))
                    }
                )
            }

            composable(Routes.LESSON_DETAIL) {
                LessonDetailScreen(
                    onBack = { navController.popBackStack() },
                    onUpgrade = { navController.navigate(Routes.UPGRADE) }
                )
            }

            composable(Routes.TUTOR) {
                TutorScreen(
                    onUpgrade = { navController.navigate(Routes.UPGRADE) }
                )
            }

            composable(Routes.PROFILE) {
                ProfileScreen(
                    onSignOut = {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onUpgrade = { navController.navigate(Routes.UPGRADE) },
                    onAccountDeleted = {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.UPGRADE) {
                UpgradeScreen(onBack = { navController.popBackStack() })
            }

            analysisGraph(navController)
        }
    }
}
