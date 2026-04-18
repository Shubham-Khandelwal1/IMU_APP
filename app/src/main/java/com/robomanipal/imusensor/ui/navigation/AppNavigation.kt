package com.robomanipal.imusensor.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.robomanipal.imusensor.ui.components.AnimatedNavBar
import com.robomanipal.imusensor.ui.components.NavItem
import com.robomanipal.imusensor.ui.screens.*
import com.robomanipal.imusensor.viewmodel.SensorViewModel
import com.robomanipal.imusensor.viewmodel.StreamViewModel

// ── Route constants ────────────────────────────────────────────────────────
object Routes {
    const val DASHBOARD   = "dashboard"
    const val ORIENTATION = "orientation"
    const val STREAM      = "stream"
    const val SETTINGS    = "settings"
    const val SENSOR_DETAIL = "sensor/{type}"
    fun sensorDetail(type: String) = "sensor/$type"
}

private val bottomNavItems = listOf(
    NavItem(Icons.Default.Dashboard, "Home"),
    NavItem(Icons.Default.Sensors,   "3D View"),
    NavItem(Icons.Default.Wifi,      "Stream"),
    NavItem(Icons.Default.Settings,  "Settings"),
)

private val bottomNavRoutes = listOf(
    Routes.DASHBOARD,
    Routes.ORIENTATION,
    Routes.STREAM,
    Routes.SETTINGS,
)

/**
 * Root composable that wires navigation, bottom bar, and shared ViewModels.
 */
@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val sensorVm: SensorViewModel = viewModel()
    val streamVm: StreamViewModel = viewModel()

    // Track which bottom tab is selected
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val selectedTab = bottomNavRoutes.indexOf(currentRoute).coerceAtLeast(0)

    // Only show bottom bar on main tabs (not on sensor detail)
    val showBottomBar = currentRoute in bottomNavRoutes

    Box(modifier = modifier.fillMaxSize()) {
        // ── Nav Host ───────────────────────────────────────────────────
        NavHost(
            navController = navController,
            startDestination = Routes.DASHBOARD,
            enterTransition  = { fadeIn(tween(260)) + slideInHorizontally { it / 5 } },
            exitTransition   = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(260)) + slideInHorizontally { -it / 5 } },
            popExitTransition  = { fadeOut(tween(200)) + slideOutHorizontally { it / 5 } },
        ) {
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    vm = sensorVm,
                    onSensorTap = { type ->
                        if (type == "orientation") {
                            // Switch to orientation tab
                            navController.navigate(Routes.ORIENTATION) {
                                popUpTo(Routes.DASHBOARD) { inclusive = false }
                                launchSingleTop = true
                            }
                        } else {
                            navController.navigate(Routes.sensorDetail(type))
                        }
                    },
                )
            }
            composable(Routes.ORIENTATION) {
                OrientationScreen(vm = sensorVm)
            }
            composable(Routes.STREAM) {
                StreamScreen(vm = streamVm)
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(vm = sensorVm)
            }
            composable(
                route = Routes.SENSOR_DETAIL,
                arguments = listOf(navArgument("type") { type = NavType.StringType }),
            ) { entry ->
                val sensorType = entry.arguments?.getString("type") ?: "accel"
                SensorDetailScreen(
                    vm = sensorVm,
                    sensorType = sensorType,
                    onBack = { navController.popBackStack() },
                )
            }
        }

        // ── Bottom navigation ──────────────────────────────────────────
        if (showBottomBar) {
            AnimatedNavBar(
                items = bottomNavItems,
                selectedIndex = selectedTab,
                onItemSelected = { idx ->
                    val route = bottomNavRoutes[idx]
                    navController.navigate(route) {
                        popUpTo(Routes.DASHBOARD) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
            )
        }
    }
}
