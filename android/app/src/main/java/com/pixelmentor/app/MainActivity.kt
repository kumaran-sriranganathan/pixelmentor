package com.pixelmentor.app

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
import com.pixelmentor.app.ui.auth.LoginScreen
import com.pixelmentor.app.ui.lessons.LessonsScreen
import com.pixelmentor.app.ui.lessons.LessonDetailScreen
import com.pixelmentor.app.ui.tutor.TutorScreen
import com.pixelmentor.app.ui.profile.ProfileScreen
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
                PixelMentorNavHost()
            }
        }
    }
}

@Composable
private fun PixelMentorNavHost() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            PixelMentorBottomBar(navController = navController)
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.LOGIN,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.LOGIN) {
                LoginScreen(
                    onAuthenticated = {
                        navController.navigate(Routes.LESSONS) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
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
                    onAnalyzePhoto = {
                        navController.navigate(AnalysisRoutes.PICKER)
                    },
                    onLessonClick = { lessonId ->
                        navController.navigate(Routes.lessonDetail(lessonId))
                    }
                )
            }

            composable(Routes.LESSON_DETAIL) {
                LessonDetailScreen(
                    onBack = { navController.popBackStack() },
                    onUpgrade = {
                        // TODO: navigate to billing when implemented
                        navController.popBackStack()
                    }
                )
            }

            composable(Routes.TUTOR) {
                TutorScreen()
            }

            composable(Routes.PROFILE) {
                ProfileScreen(
                    onSignOut = {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            analysisGraph(navController)
        }
    }
}
