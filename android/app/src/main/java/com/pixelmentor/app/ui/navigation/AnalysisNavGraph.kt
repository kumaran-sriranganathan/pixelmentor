package com.pixelmentor.app.ui.navigation

// ─────────────────────────────────────────────────────────────────────────────
// Add these routes to your existing NavGraph / navigation setup.
// This snippet shows how to wire PhotoAnalysisScreen → AnalysisResultsScreen.
// ─────────────────────────────────────────────────────────────────────────────

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.pixelmentor.app.Routes
import com.pixelmentor.app.ui.analyze.AnalysisResultsScreen
import com.pixelmentor.app.ui.analyze.PhotoAnalysisScreen
import com.pixelmentor.app.ui.analyze.PhotoAnalysisViewModel

object AnalysisRoutes {
    const val PICKER  = "analyze/picker"
    const val RESULTS = "analyze/results"
}

/**
 * Call this from within your NavHost { ... } block.
 *
 * The two screens SHARE the same PhotoAnalysisViewModel instance
 * (scoped to the same BackStackEntry via the navBackStackEntry trick below).
 *
 * Usage inside NavHost:
 *
 *   analysisGraph(navController)
 */
fun NavGraphBuilder.analysisGraph(navController: NavController) {

    composable(AnalysisRoutes.PICKER) { backStackEntry ->
        // ViewModel is shared across both analysis destinations
        val viewModel: PhotoAnalysisViewModel = hiltViewModel(backStackEntry)

        PhotoAnalysisScreen(
            onNavigateToResults = {
                navController.navigate(AnalysisRoutes.RESULTS)
            },
            viewModel = viewModel
        )
    }

    composable(AnalysisRoutes.RESULTS) { backStackEntry ->
        // Re-use the PICKER's backstack entry to share the same VM
        val pickerEntry = remember(backStackEntry) {
            navController.getBackStackEntry(AnalysisRoutes.PICKER)
        }
        val viewModel: PhotoAnalysisViewModel = hiltViewModel(pickerEntry)

        AnalysisResultsScreen(
            onBack = { navController.popBackStack() },
            onAnalyzeAnother = {
                // Pop results + picker, navigate fresh
                navController.popBackStack(AnalysisRoutes.PICKER, inclusive = true)
                navController.navigate(AnalysisRoutes.PICKER)
            },
            onLessonClick = { _ ->
                navController.navigate(Routes.LESSONS) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            viewModel = viewModel
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Needed import in the composable above — add to the file that calls this:
// import androidx.compose.runtime.remember
// ─────────────────────────────────────────────────────────────────────────────
