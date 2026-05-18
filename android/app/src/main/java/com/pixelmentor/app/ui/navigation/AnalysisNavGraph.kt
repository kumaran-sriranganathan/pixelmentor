package com.pixelmentor.app.ui.navigation

import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.pixelmentor.app.Routes
import com.pixelmentor.app.ui.analyze.AnalysisResultsScreen
import com.pixelmentor.app.ui.analyze.PhotoAnalysisScreen
import com.pixelmentor.app.ui.analyze.PhotoAnalysisViewModel

object AnalysisRoutes {
    const val GRAPH   = "analyze"
    const val PICKER  = "analyze/picker"
    const val RESULTS = "analyze/results"
}

/**
 * Nested nav graph so both screens share the same PhotoAnalysisViewModel
 * scoped to the graph — no fragile getBackStackEntry() needed.
 */
fun NavGraphBuilder.analysisGraph(navController: NavController) {

    navigation(
        startDestination = AnalysisRoutes.PICKER,
        route = AnalysisRoutes.GRAPH,
    ) {
        composable(AnalysisRoutes.PICKER) { backStackEntry ->
            // Scope VM to the parent graph entry so RESULTS can share it
            val graphEntry = remember(backStackEntry) {
                navController.getBackStackEntry(AnalysisRoutes.GRAPH)
            }
            val viewModel: PhotoAnalysisViewModel = hiltViewModel(graphEntry)

            PhotoAnalysisScreen(
                onNavigateToResults = {
                    navController.navigate(AnalysisRoutes.RESULTS)
                },
                viewModel = viewModel
            )
        }

        composable(AnalysisRoutes.RESULTS) { backStackEntry ->
            // Same graph entry — same ViewModel instance
            val graphEntry = remember(backStackEntry) {
                navController.getBackStackEntry(AnalysisRoutes.GRAPH)
            }
            val viewModel: PhotoAnalysisViewModel = hiltViewModel(graphEntry)

            AnalysisResultsScreen(
                onBack = { navController.popBackStack() },
                onAnalyzeAnother = {
                    navController.popBackStack(AnalysisRoutes.PICKER, inclusive = false)
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
}
