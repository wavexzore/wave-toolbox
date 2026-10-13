package com.wavexzore.toolbox.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wavexzore.toolbox.core.design.theme.Wave
import com.wavexzore.toolbox.feature.settings.DiagnosticsScreen
import com.wavexzore.toolbox.feature.settings.EventLogScreen
import com.wavexzore.toolbox.feature.settings.FeatureAppsScreen
import com.wavexzore.toolbox.feature.settings.FeatureSettingsScreen
import com.wavexzore.toolbox.feature.settings.SettingsScreen
import com.wavexzore.toolbox.feature.settings.SetupGuideScreen
import com.wavexzore.toolbox.feature.settings.SnapshotScreen
import com.wavexzore.toolbox.feature.settings.StatsScreen
import com.wavexzore.toolbox.ui.components.WaveBottomBar
import com.wavexzore.toolbox.ui.home.HomeScreen

object Routes {
    const val Home = "home"
    const val Settings = "settings"
    const val EventLog = "event_log"
    const val SetupGuide = "setup_guide"
    const val Snapshot = "snapshot"
    const val Stats = "stats"
    const val Diagnostics = "diagnostics"
    const val Feature = "feature/{featureId}"
    const val FeatureApps = "feature/{featureId}/apps"

    fun feature(featureId: String): String = "feature/$featureId"
    fun featureApps(featureId: String): String = "feature/$featureId/apps"
}

/** 导航图：功能/设置 为主 Tab（带底栏）；特性详情等为二级页（隐藏底栏）。 */
@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val showBottomBar = route == Routes.Home || route == Routes.Settings

    Scaffold(
        containerColor = Wave.Screen,
        bottomBar = {
            if (showBottomBar) {
                WaveBottomBar(
                    current = if (route == Routes.Home) 0 else 1,
                    onSelect = { index ->
                        val target = if (index == 0) Routes.Home else Routes.Settings
                        if (target != route) {
                            navController.navigate(target) {
                                popUpTo(Routes.Home) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.Home,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.Home) {
                HomeScreen(
                    onOpenSettings = { navController.navigate(Routes.Settings) },
                    onOpenSetupGuide = { navController.navigate(Routes.SetupGuide) },
                    onOpenFeature = { featureId -> navController.navigate(Routes.feature(featureId)) },
                )
            }
            composable(Routes.Settings) {
                SettingsScreen(
                    onOpenEventLog = { navController.navigate(Routes.EventLog) },
                    onOpenSetupGuide = { navController.navigate(Routes.SetupGuide) },
                    onOpenSnapshot = { navController.navigate(Routes.Snapshot) },
                    onOpenStats = { navController.navigate(Routes.Stats) },
                    onOpenDiagnostics = { navController.navigate(Routes.Diagnostics) },
                )
            }
            composable(Routes.EventLog) {
                EventLogScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SetupGuide) {
                SetupGuideScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.Snapshot) {
                SnapshotScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.Stats) {
                StatsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.Diagnostics) {
                DiagnosticsScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.Feature,
                arguments = listOf(navArgument("featureId") { type = NavType.StringType }),
            ) { entry ->
                val featureId = entry.arguments?.getString("featureId").orEmpty()
                FeatureSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenApps = { navController.navigate(Routes.featureApps(featureId)) },
                    onOpenDiagnostics = { navController.navigate(Routes.Diagnostics) },
                )
            }
            composable(
                route = Routes.FeatureApps,
                arguments = listOf(navArgument("featureId") { type = NavType.StringType }),
            ) {
                FeatureAppsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
