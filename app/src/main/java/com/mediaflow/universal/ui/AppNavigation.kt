package com.mediaflow.universal.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mediaflow.universal.R
import com.mediaflow.universal.ui.downloads.DownloadsScreen
import com.mediaflow.universal.ui.downloads.DownloadsViewModel
import com.mediaflow.universal.ui.home.HomeScreen
import com.mediaflow.universal.ui.home.HomeViewModel
import com.mediaflow.universal.ui.settings.SettingsScreen
import com.mediaflow.universal.ui.settings.SettingsViewModel

private object Routes {
    const val HOME = "home"
    const val DOWNLOADS = "downloads"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavigation(sharedLink: String? = null) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val homeViewModel: HomeViewModel = viewModel()
    val downloadsViewModel: DownloadsViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute == Routes.HOME,
                    onClick = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text(stringResource(R.string.home_tab)) },
                    alwaysShowLabel = true
                )
                NavigationBarItem(
                    selected = currentRoute == Routes.DOWNLOADS,
                    onClick = {
                        navController.navigate(Routes.DOWNLOADS) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Filled.Download, contentDescription = null) },
                    label = { Text(stringResource(R.string.downloads)) }
                )
                NavigationBarItem(
                    selected = currentRoute == Routes.SETTINGS,
                    onClick = {
                        navController.navigate(Routes.SETTINGS) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.settings)) }
                )
            }
        }
    ) { padding ->
        NavHost(navController = navController, startDestination = Routes.HOME, Modifier.padding(padding)) {
            composable(Routes.HOME) {
                HomeScreen(
                    viewModel = homeViewModel,
                    sharedLink = sharedLink,
                    onGoToDownloads = {
                        navController.navigate(Routes.DOWNLOADS) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Routes.DOWNLOADS) { DownloadsScreen(downloadsViewModel) }
            composable(Routes.SETTINGS) { SettingsScreen(settingsViewModel) }
        }
    }
}
