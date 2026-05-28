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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
                PixelMentorNavHost(startRoute = startRoute)
            }
        }
    }

    // Handle deep-link when app is already running in the background
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate() // Recompose with the new intent so startRoute is re-evaluated
    }
}

@Composable
private fun PixelMentorNavHost(startRoute: String = Routes.LOGIN) {
    val navController = rememberNavController()

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
                        navController.navigate(Routes.LESSONS) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    },
                    onForgotPassword = {
                        navController.navigate(Routes.FORGOT_PASSWORD)
                    }
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
                    }
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

            composable(Routes.TUTOR) { TutorScreen() }

            composable(Routes.PROFILE) {
                val activity = (androidx.compose.ui.platform.LocalContext.current as? android.app.Activity)
                ProfileScreen(
                    onSignOut = {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                        // Recreate the Activity so Hilt destroys all ViewModel
                        // instances — prevents the next user seeing stale profile
                        // data from the previous session.
                        activity?.recreate()
                    },
                    onUpgrade = { navController.navigate(Routes.UPGRADE) },
                    onAccountDeleted = {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                        activity?.recreate()
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
