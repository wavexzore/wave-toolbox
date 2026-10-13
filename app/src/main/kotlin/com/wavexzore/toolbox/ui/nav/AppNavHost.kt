package com.wavexzore.toolbox.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wavexzore.toolbox.ui.home.HomeScreen
import com.wavexzore.toolbox.feature.settings.EventLogScreen
import com.wavexzore.toolbox.feature.settings.SettingsScreen

object Routes {
    const val Home = "home"
    const val Settings = "settings"
    const val EventLog = "event_log"
}

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.Home) {
        composable(Routes.Home) {
            HomeScreen(onOpenSettings = { navController.navigate(Routes.Settings) })
        }
        composable(Routes.Settings) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenEventLog = { navController.navigate(Routes.EventLog) },
            )
        }
        composable(Routes.EventLog) {
            EventLogScreen(onBack = { navController.popBackStack() })
        }
    }
}
