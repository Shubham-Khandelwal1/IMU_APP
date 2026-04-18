package com.robomanipal.imusensor.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import kotlinx.coroutines.launch

// ── Route constants ────────────────────────────────────────────────────────
object Routes {
    const val MAIN          = "main"
    const val SENSOR_DETAIL = "sensor/{type}"
    fun sensorDetail(type: String) = "sensor/$type"
}

private val bottomNavItems = listOf(
    NavItem(Icons.Default.Dashboard, "Home"),
    NavItem(Icons.Default.ViewInAr,  "3D View"),   // ← updated icon
    NavItem(Icons.Default.Wifi,      "Stream"),
    NavItem(Icons.Default.Settings,  "Settings"),
)

/**
 * Root composable.
 *
 * Architecture:
 *   NavHost
 *     ├── "main"         → MainTabs  (HorizontalPager with swipe + bottom nav)
 *     └── "sensor/{type}"→ SensorDetailScreen  (push, back arrow)
 */
@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val sensorVm: SensorViewModel = viewModel()
    val streamVm: StreamViewModel = viewModel()

    NavHost(
        navController      = navController,
        startDestination   = Routes.MAIN,
        modifier           = modifier,
        enterTransition    = {
            fadeIn(tween(280)) + slideInHorizontally { it / 3 }
        },
        exitTransition     = { fadeOut(tween(200)) },
        popEnterTransition = {
            fadeIn(tween(280)) + slideInHorizontally { -it / 3 }
        },
        popExitTransition  = {
            fadeOut(tween(200)) + slideOutHorizontally { it / 3 }
        },
    ) {
        // ── Main tabs (swipeable) ──────────────────────────────────────
        composable(Routes.MAIN) {
            MainTabs(
                sensorVm  = sensorVm,
                streamVm  = streamVm,
                onPushDetail = { type -> navController.navigate(Routes.sensorDetail(type)) },
            )
        }

        // ── Sensor detail (push screen) ────────────────────────────────
        composable(
            route     = Routes.SENSOR_DETAIL,
            arguments = listOf(navArgument("type") { type = NavType.StringType }),
        ) { entry ->
            val sensorType = entry.arguments?.getString("type") ?: "accel"
            SensorDetailScreen(
                vm         = sensorVm,
                sensorType = sensorType,
                onBack     = { navController.popBackStack() },
            )
        }
    }
}

/**
 * The four swipeable tabs + bottom nav.
 * HorizontalPager drives both swipe and tab-click navigation.
 */
@Composable
private fun MainTabs(
    sensorVm: SensorViewModel,
    streamVm: StreamViewModel,
    onPushDetail: (String) -> Unit,
) {
    val scope      = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 4 })

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Swipeable pages ────────────────────────────────────────────
        HorizontalPager(
            state    = pagerState,
            modifier = Modifier.fillMaxSize(),
            // Disable swipe on Stream/Settings to avoid accidental dismissal
            // while typing IP — enable everywhere for now, easy to restrict later
            userScrollEnabled = true,
        ) { page ->
            when (page) {
                0 -> DashboardScreen(
                    vm          = sensorVm,
                    onSensorTap = { type ->
                        if (type == "orientation") {
                            scope.launch { pagerState.animateScrollToPage(1) }
                        } else {
                            onPushDetail(type)
                        }
                    },
                )
                1 -> OrientationScreen(vm = sensorVm)
                2 -> StreamScreen(vm = streamVm)
                3 -> SettingsScreen(vm = sensorVm)
            }
        }

        // ── Bottom navigation ──────────────────────────────────────────
        AnimatedNavBar(
            items         = bottomNavItems,
            selectedIndex = pagerState.currentPage,
            onItemSelected = { idx ->
                scope.launch { pagerState.animateScrollToPage(idx) }
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
