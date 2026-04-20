package com.pixelmentor.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pixelmentor.app.ui.auth.LoginScreen
import com.pixelmentor.app.ui.lessons.LessonsScreen
import com.pixelmentor.app.ui.theme.PixelMentorTheme
import com.pixelmentor.app.ui.navigation.analysisGraph
import com.pixelmentor.app.ui.navigation.AnalysisRoutes
import dagger.hilt.android.AndroidEntryPoint

// ── Navigation routes ─────────────────────────────────────────────────────────

object Routes {
    const val LOGIN = "login"
    const val LESSONS = "lessons"
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

    NavHost(
        navController = navController,
        startDestination = Routes.LOGIN
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
                }
            )
        }

        analysisGraph(navController)
    }
}
