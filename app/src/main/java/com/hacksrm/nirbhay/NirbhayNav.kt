package com.hacksrm.nirbhay

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.hacksrm.nirbhay.screens.*
import com.hacksrm.nirbhay.screens.Stealth_Dashboard.StealthDashboardScreen

// Simple route names
private const val ROUTE_HOME = "home"
private const val ROUTE_DASHBOARD = "dashboard"
private const val ROUTE_SOS = "sos"
private const val ROUTE_SETTINGS = "settings"

/**
 * Root navigation composable.
 *
 * @param sosTriggerReason When this state becomes non-null (e.g.,
 *   "SCREAM_DETECTED" or "FALL_DETECTED") the nav host automatically
 *   navigates to the SOS countdown screen, then resets the state to
 *   null so subsequent triggers can fire again.
 */
@Composable
fun NirbhayNav(
    modifier: Modifier = Modifier,
    sosTriggerReason: MutableState<String?> = mutableStateOf(null),
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_HOME

    // ── Auto-navigate to SOS when a trigger fires ────────────
    val triggerReason = sosTriggerReason.value
    LaunchedEffect(triggerReason) {
        if (triggerReason != null && currentRoute != ROUTE_SOS) {
            navController.navigate(ROUTE_SOS) {
                popUpTo(navController.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
            }
            // Reset so the next broadcast can trigger again
            sosTriggerReason.value = null
        }
    }

    // Map current route to selected index for BottomNavBar
    val selectedIndex = when (currentRoute) {
        ROUTE_HOME -> 0
        ROUTE_DASHBOARD -> 1
        ROUTE_SOS -> 2
        ROUTE_SETTINGS -> 3
        else -> 0
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Nav host occupies flexible area
        Box(modifier = Modifier
            .weight(1f)
            .fillMaxWidth()) {
            NavHost(navController = navController, startDestination = ROUTE_HOME, modifier = Modifier.fillMaxSize()) {
                composable(ROUTE_HOME) {
                    HomeScreen(onSosClick = {
                        navController.navigate(ROUTE_SOS) {
                            launchSingleTop = true
                        }
                    })
                }
                composable(ROUTE_DASHBOARD) { StealthDashboardScreen() }
                composable(ROUTE_SOS) {
                    SosCountdownScreen(
                        onBack = { navController.popBackStack() },
                        onCancelled = { navController.popBackStack() }
                    )
                }
                composable(ROUTE_SETTINGS) { SettingsScreen() }
            }
        }

        // Bottom nav pinned to bottom, navigate on click
        BottomNavBar(selectedIndex = selectedIndex, onItemSelected = { index ->
            val route = navRouteForIndex(index)
            if (route != currentRoute) {
                navController.navigate(route) {
                    // Pop up to the start destination to avoid building a large back stack
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        })
    }
}

// Helper so other components can navigate using index mapping
fun navRouteForIndex(index: Int): String = when (index) {
    0 -> ROUTE_HOME
    1 -> ROUTE_DASHBOARD
    2 -> ROUTE_SOS
    3 -> ROUTE_SETTINGS
    else -> ROUTE_HOME
}
