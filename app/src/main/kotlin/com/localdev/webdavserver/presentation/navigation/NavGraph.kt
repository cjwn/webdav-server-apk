package com.localdev.webdavserver.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.localdev.webdavserver.di.AppModule
import com.localdev.webdavserver.presentation.files.FilesScreen
import com.localdev.webdavserver.presentation.home.HomeScreen
import com.localdev.webdavserver.presentation.log.LogScreen
import com.localdev.webdavserver.presentation.settings.SettingsScreen
import com.localdev.webdavserver.presentation.setup.SetupWizardScreen
import com.localdev.webdavserver.presentation.users.UsersScreen

/**
 * Root navigation graph. Shows the setup wizard until the first admin is
 * created, then the tabbed main UI.
 */
@Composable
fun NavGraph(setupComplete: Boolean) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val onlineUsers by AppModule.webDavEngine.state.collectAsState()
    val users by AppModule.userRepository.users.collectAsState()
    val unreadErrors by AppModule.logRepository.unreadErrors.collectAsState()

    val showBottomBar = currentRoute in setOf(
        Screen.Home.route, Screen.Files.route, Screen.Users.route,
        Screen.Log.route, Screen.Settings.route
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomNavBar(
                    currentRoute = currentRoute,
                    onlineUsers = if (onlineUsers.isRunning) users.size else 0,
                    unreadErrors = unreadErrors,
                    onTabSelected = { tab ->
                        navController.navigate(tab.screen.route) {
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (setupComplete) Screen.Home.route else Screen.Setup.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Setup.route) {
                SetupWizardScreen(
                    onSetupComplete = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Setup.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Home.route) {
                HomeScreen(
                    onOpenSettings = {
                        navController.navigate(Screen.Settings.route) {
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(Screen.Files.route) { FilesScreen() }
            composable(Screen.Users.route) { UsersScreen() }
            composable(Screen.Log.route) { LogScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}
