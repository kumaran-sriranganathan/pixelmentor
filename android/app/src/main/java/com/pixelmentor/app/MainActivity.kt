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
                // Check if launched from a password reset deep link
                val startRoute = if (intent?.data?.scheme == "io.supabase.pixelmentor") {
                    Routes.RESET_PASSWORD
                } else {
                    Routes.LOGIN
                }
                PixelMentorNavHost(startRoute = startRoute)
            }
        }
    }

    // Handle deep-link when app is already running
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
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
                ProfileScreen(
                    onSignOut = {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onUpgrade = { navController.navigate(Routes.UPGRADE) }
                )
            }

            composable(Routes.UPGRADE) {
                UpgradeScreen(onBack = { navController.popBackStack() })
            }

            analysisGraph(navController)
        }
    }
}
