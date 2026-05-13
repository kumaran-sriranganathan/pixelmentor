package com.pixelmentor.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    object Lessons : BottomNavItem(
        route = "lessons",
        label = "Lessons",
        icon = Icons.Outlined.AutoStories
    )
    object Analyze : BottomNavItem(
        route = AnalysisRoutes.PICKER,
        label = "Analyze",
        icon = Icons.Outlined.CameraAlt
    )
    object Tutor : BottomNavItem(
        route = "tutor",
        label = "Tutor",
        icon = Icons.Outlined.SmartToy
    )
    object Profile : BottomNavItem(
        route = "profile",
        label = "Profile",
        icon = Icons.Outlined.Person
    )
}

val bottomNavItems = listOf(
    BottomNavItem.Lessons,
    BottomNavItem.Analyze,
    BottomNavItem.Tutor,
    BottomNavItem.Profile,
)
