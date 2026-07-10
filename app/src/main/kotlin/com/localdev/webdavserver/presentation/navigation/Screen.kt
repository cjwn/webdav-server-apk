package com.localdev.webdavserver.presentation.navigation

import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.localdev.webdavserver.R

/**
 * App destinations.
 */
sealed class Screen(val route: String) {
    data object Setup : Screen("setup")
    data object Home : Screen("home")
    data object Files : Screen("files")
    data object Users : Screen("users")
    data object Log : Screen("log")
    data object Settings : Screen("settings")
}

/**
 * Bottom-bar tabs. Prefer [icon] (core Material icons) or [iconRes] (app drawables).
 */
enum class Tab(
    val screen: Screen,
    val label: String,
    val icon: ImageVector? = null,
    @DrawableRes val iconRes: Int? = null
) {
    HOME(Screen.Home, "Home", icon = Icons.Filled.Home),
    FILES(Screen.Files, "Files", iconRes = R.drawable.ic_folder),
    USERS(Screen.Users, "Users", icon = Icons.Filled.Person),
    LOG(Screen.Log, "Log", icon = Icons.AutoMirrored.Filled.List),
    SETTINGS(Screen.Settings, "Settings", icon = Icons.Filled.Settings)
}
