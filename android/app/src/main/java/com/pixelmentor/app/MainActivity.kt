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
        startDestination = Routes.LOGIN,
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onAuthenticated = {
                    navController.navigate(Routes.LESSONS) {
                        // Clear back stack so back button doesn't return to login
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
                }
            )
        }
    }
}
