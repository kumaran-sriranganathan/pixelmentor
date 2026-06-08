package com.pixelmentor.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pixelmentor.app.data.auth.AuthRepository
import com.pixelmentor.app.domain.model.AuthState
import com.pixelmentor.app.ui.auth.ForgotPasswordScreen
import com.pixelmentor.app.ui.auth.LoginScreen
import com.pixelmentor.app.ui.auth.ResetPasswordScreen
import com.pixelmentor.app.ui.lessons.LessonsScreen
import androidx.hilt.navigation.compose.hiltViewModel
import com.pixelmentor.app.ui.lessons.LessonsViewModel
import com.pixelmentor.app.ui.lessons.LessonDetailScreen
import com.pixelmentor.app.ui.tutor.TutorScreen
import com.pixelmentor.app.ui.profile.ProfileScreen
import com.pixelmentor.app.ui.upgrade.UpgradeScreen
import com.pixelmentor.app.ui.theme.PixelMentorTheme
import com.pixelmentor.app.ui.navigation.AnalysisRoutes
import com.pixelmentor.app.ui.navigation.PixelMentorBottomBar
import com.pixelmentor.app.ui.navigation.analysisGraph
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── Navigation routes ─────────────────────────────────────────────────────────

object Routes {
    // Auth graph
    const val LOGIN           = "login"
    const val FORGOT_PASSWORD = "forgot_password"
    const val RESET_PASSWORD  = "reset_password"

    // App graph
    const val LESSONS         = "lessons"
    const val TUTOR           = "tutor"
    const val PROFILE         = "profile"
    const val UPGRADE         = "upgrade"
    const val LESSON_DETAIL   = "lessons/{lessonId}"

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

        // If launched by a password-reset deep link, exchange the recovery
        // tokens BEFORE the UI renders so updatePassword() has a valid session.
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
                PixelMentorRoot(
                    authRepository = authRepository,
                    deepLinkUrl = intent?.data?.toString(),
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
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

// ── Root — owns auth state, decides which graph to show ──────────────────────
//
// This is the key architectural fix. Previously Login and Lessons shared a
// single NavHost — so navigating to LOGIN with popUpTo still left Lessons
// reachable, and the bottom bar could trigger a bounce back.
//
// Now authState is the sole gate. When Unauthenticated → AuthNavHost renders
// (no bottom bar, no app routes). When Authenticated → AppNavHost renders
// (bottom bar, all app routes). It is physically impossible to see an
// authenticated screen while unauthenticated, regardless of nav stack state.

@Composable
private fun PixelMentorRoot(
    authRepository: AuthRepository,
    deepLinkUrl: String?,
) {
    val authState by authRepository.authState.collectAsStateWithLifecycle()

    // Session-expired message forwarded from force-logout signal into AuthNavHost
    var sessionExpiredMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        authRepository.forceLogout.collect { reason ->
            sessionExpiredMessage = reason
            // No navigation needed — flipping authState to Unauthenticated
            // (done inside notifyForceLogout) automatically swaps to AuthNavHost
        }
    }

    when (authState) {
        // ── Still resolving session on cold start ─────────────────────────────
        is AuthState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        // ── Not signed in → show auth screens only ────────────────────────────
        // AppNavHost does not exist in this branch — Lessons/Tutor/Profile are
        // unreachable no matter what the nav stack contains.
        is AuthState.Unauthenticated -> {
            AuthNavHost(
                deepLinkUrl = deepLinkUrl,
                sessionExpiredMessage = sessionExpiredMessage,
                onAuthenticated = { sessionExpiredMessage = null },
            )
        }

        // ── Signed in → show app screens only ────────────────────────────────
        // LoginScreen does not exist in this branch — no bounce possible.
        is AuthState.Authenticated -> {
            AppNavHost()
        }
    }
}

// ── Auth nav host — Login, ForgotPassword, ResetPassword ─────────────────────
// No Scaffold bottom bar. No app routes.

@Composable
private fun AuthNavHost(
    deepLinkUrl: String?,
    sessionExpiredMessage: String?,
    onAuthenticated: () -> Unit,
) {
    val navController = rememberNavController()

    // Determine start destination from deep link
    val startDestination = when {
        deepLinkUrl?.contains("type=recovery") == true ||
        deepLinkUrl?.contains("access_token=") == true -> Routes.RESET_PASSWORD
        else -> Routes.LOGIN
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onAuthenticated = {
                    // authState will flip to Authenticated — PixelMentorRoot
                    // will swap to AppNavHost automatically. No nav call needed.
                    onAuthenticated()
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
            ResetPasswordScreen(
                onPasswordReset = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.RESET_PASSWORD) { inclusive = true }
                    }
                },
                deepLinkUrl = deepLinkUrl,
            )
        }
    }
}

// ── App nav host — all authenticated screens ──────────────────────────────────
// Only rendered when authState is Authenticated. Sign-out needs no navigation
// call — flipping authState to Unauthenticated swaps back to AuthNavHost.

@Composable
private fun AppNavHost() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = { PixelMentorBottomBar(navController = navController) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.LESSONS,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.LESSONS) {
                val lessonsViewModel: LessonsViewModel = hiltViewModel()
                // Reload completion ticks when returning from LessonDetail
                val lessonCompleted = it.savedStateHandle
                    .getStateFlow("lesson_completed", false)
                    .collectAsStateWithLifecycle()
                LaunchedEffect(lessonCompleted.value) {
                    if (lessonCompleted.value) {
                        lessonsViewModel.reloadCompletions()
                        it.savedStateHandle["lesson_completed"] = false
                    }
                }
                LessonsScreen(
                    onSignOut = { /* authState handles nav — no-op needed */ },
                    onAnalyzePhoto = { navController.navigate(AnalysisRoutes.PICKER) },
                    onLessonClick = { lessonId ->
                        navController.navigate(Routes.lessonDetail(lessonId))
                    },
                    viewModel = lessonsViewModel,
                )
            }

            composable(Routes.LESSON_DETAIL) {
                LessonDetailScreen(
                    onBack = { navController.popBackStack() },
                    onUpgrade = { navController.navigate(Routes.UPGRADE) },
                    onLessonCompleted = {
                        // Reload lessons so the green tick appears immediately on return
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("lesson_completed", true)
                    }
                )
            }

            composable(Routes.TUTOR) {
                TutorScreen(
                    onUpgrade = { navController.navigate(Routes.UPGRADE) }
                )
            }

            composable(Routes.PROFILE) {
                ProfileScreen(
                    onSignOut = { /* authState handles nav — no-op needed */ },
                    onUpgrade = { navController.navigate(Routes.UPGRADE) },
                    onAccountDeleted = { /* authState handles nav — no-op needed */ }
                )
            }

            composable(Routes.UPGRADE) {
                UpgradeScreen(onBack = { navController.popBackStack() })
            }

            analysisGraph(navController)
        }
    }
}
