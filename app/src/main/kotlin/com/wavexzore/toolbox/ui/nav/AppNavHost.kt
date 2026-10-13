package com.wavexzore.toolbox.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wavexzore.toolbox.feature.settings.EventLogScreen
import com.wavexzore.toolbox.feature.settings.FeatureSettingsScreen
import com.wavexzore.toolbox.feature.settings.SettingsScreen
import com.wavexzore.toolbox.feature.settings.SetupGuideScreen
import com.wavexzore.toolbox.feature.settings.SnapshotScreen
import com.wavexzore.toolbox.ui.home.HomeScreen

object Routes {
    const val Home = "home"
    const val Settings = "settings"
    const val EventLog = "event_log"
    const val SetupGuide = "setup_guide"
    const val Snapshot = "snapshot"
    const val Feature = "feature/{featureId}"

    fun feature(featureId: String): String = "feature/$featureId"
}

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.Home) {
        composable(Routes.Home) {
            HomeScreen(
                onOpenSettings = { navController.navigate(Routes.Settings) },
                onOpenSetupGuide = { navController.navigate(Routes.SetupGuide) },
                onOpenFeature = { featureId -> navController.navigate(Routes.feature(featureId)) },
            )
        }
        composable(Routes.Settings) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenEventLog = { navController.navigate(Routes.EventLog) },
                onOpenSetupGuide = { navController.navigate(Routes.SetupGuide) },
                onOpenSnapshot = { navController.navigate(Routes.Snapshot) },
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
        composable(
            route = Routes.Feature,
            arguments = listOf(navArgument("featureId") { type = NavType.StringType }),
        ) {
            FeatureSettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
