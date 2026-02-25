package com.hacksrm.nirbhay

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.hacksrm.nirbhay.screens.*
import com.hacksrm.nirbhay.screens.Onboarding.AddTrustedContactsPage
import com.hacksrm.nirbhay.screens.Onboarding.LandingPage
import com.hacksrm.nirbhay.screens.Onboarding.LoginSignupPage
import com.hacksrm.nirbhay.screens.Stealth_Dashboard.StealthDashboardScreen
import com.hacksrm.nirbhay.screens.Stealth_Dashboard.EmergencyContactPage
import com.hacksrm.nirbhay.screens.Onboarding.LoginSignupPage

// Route names
private const val ROUTE_LANDING = "landing"
private const val ROUTE_LOGIN = "login"
private const val ROUTE_GUARDIAN_ONBOARD = "guardian_onboard"
private const val ROUTE_HOME = "home"
private const val ROUTE_DASHBOARD = "dashboard"
private const val ROUTE_SOS = "sos"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_LOGIN = "login"
private const val ROUTE_EMERGENCY_CONTACT = "emergency_contact"

// SharedPreferences key to track whether user has completed onboarding
private const val PREFS_NAME = "nirbhay_prefs"
private const val KEY_ONBOARDED = "onboarded"

/**
 * Root navigation composable.
 *
 * Flow:
 *  - If user has NOT completed onboarding → LandingPage → LoginSignupPage
 *      • Login tab  → HomeScreen
 *      • Signup tab → GuardianOnboarding → HomeScreen
 *  - If user HAS completed onboarding → straight to HomeScreen
 *
 * @param sosTriggerReason When non-null, auto-navigates to SOS countdown.
 */
@Composable
fun NirbhayNav(
    modifier: Modifier = Modifier,
    sosTriggerReason: MutableState<String?> = mutableStateOf(null),
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE) }
    val isOnboarded = remember { prefs.getBoolean(KEY_ONBOARDED, false) }

    // Pick the start destination based on onboarding status
    val startDestination = if (isOnboarded) ROUTE_HOME else ROUTE_LANDING

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: startDestination

    // ── Remember the last trigger reason so the SOS screen can display it ──
    // This persists the value even after sosTriggerReason is reset to null.
    var lastTriggerReason by remember { mutableStateOf<String?>(null) }

    // ── Auto-navigate to SOS when a trigger fires ────────────
    val triggerReason = sosTriggerReason.value
    LaunchedEffect(triggerReason) {
        if (triggerReason != null && currentRoute != ROUTE_SOS) {
            lastTriggerReason = triggerReason
            navController.navigate(ROUTE_SOS) {
                popUpTo(navController.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
            }
            // Reset so the next broadcast can trigger again
            sosTriggerReason.value = null
        }
    }

    // Helper: mark onboarding complete and navigate to Home, clearing back stack
    fun completeOnboardingAndGoHome() {
        prefs.edit().putBoolean(KEY_ONBOARDED, true).apply()
        navController.navigate(ROUTE_HOME) {
            popUpTo(ROUTE_LANDING) { inclusive = true }
            launchSingleTop = true
        }
    }

    // Show bottom nav only on main app screens (not onboarding)
    val showBottomNav = currentRoute in listOf(ROUTE_HOME, ROUTE_DASHBOARD, ROUTE_SOS, ROUTE_SETTINGS)

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
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.fillMaxSize()
            ) {
                // ── Onboarding screens ──
                composable(ROUTE_LANDING) {
                    LandingPage(
                        onGetStarted = { navController.navigate(ROUTE_LOGIN) },
                        onLogIn = { navController.navigate(ROUTE_LOGIN) }
                    )
                }

                composable(ROUTE_LOGIN) {
                    LoginSignupPage(
                        onLoginSuccess = {
                            // Login → straight to Home
                            completeOnboardingAndGoHome()
                        },
                        onSignupSuccess = {
                            // Signup → Guardian Onboarding first
                            navController.navigate(ROUTE_GUARDIAN_ONBOARD) {
                                popUpTo(ROUTE_LOGIN) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    )
                }

                composable(ROUTE_GUARDIAN_ONBOARD) {
                    AddTrustedContactsPage(
                        onBack = { navController.popBackStack() },
                        onSaveAndContinue = {
                            // Guardian onboarding done → Home
                            completeOnboardingAndGoHome()
                        }
                    )
                }

                // ── Main app screens ──
                composable(ROUTE_HOME) {
                    HomeScreen(onSosClick = {
                        lastTriggerReason = null
                        navController.navigate(ROUTE_SOS) {
                            launchSingleTop = true
                        }
                    })
                }
                composable(ROUTE_DASHBOARD) { StealthDashboardScreen() }
                // Login route — pass navigation lambdas so LoginSignupPage can navigate to EmergencyContact
                composable(ROUTE_LOGIN) {
                    LoginSignupPage(
                        onLoginSuccess = {
                            // after successful login go to home and clear login from backstack
                            navController.navigate(ROUTE_HOME) {
                                popUpTo(ROUTE_LOGIN) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        onSignupSuccess = {
                            // For sign-up success we could go to guardian onboarding; default to home for now
                            navController.navigate(ROUTE_HOME) {
                                popUpTo(ROUTE_LOGIN) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        onAddEmergency = {
                            navController.navigate(ROUTE_EMERGENCY_CONTACT)
                        }
                    )
                }

                // Emergency Contact page
                composable(ROUTE_EMERGENCY_CONTACT) {
                    EmergencyContactPage(onBack = { navController.popBackStack() })
                }
                composable(ROUTE_SOS) {
                    SosCountdownScreen(
                        triggerReason = lastTriggerReason,
                        onBack = { navController.popBackStack() },
                        onCancelled = { navController.popBackStack() }
                    )
                }
                composable(ROUTE_SETTINGS) { SettingsScreen() }
            }
        }

        // Bottom nav pinned to bottom (only on main screens, hidden during onboarding)
        if (showBottomNav) {
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
}

// Helper so other components can navigate using index mapping
fun navRouteForIndex(index: Int): String = when (index) {
    0 -> ROUTE_HOME
    1 -> ROUTE_DASHBOARD
    2 -> ROUTE_SOS
    3 -> ROUTE_SETTINGS
    else -> ROUTE_HOME
}
