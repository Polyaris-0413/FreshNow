package com.freshnow.app.ui

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.freshnow.app.R
import com.freshnow.app.ui.component.FreshNowSubPage
import com.freshnow.app.ui.home.HomeRoute
import com.freshnow.app.ui.scan.ScanScreen
import com.freshnow.app.ui.settings.SettingsScreen
import com.freshnow.app.ui.theme.FreshNowTransitions

object FreshNowRoute {
    const val HOME = "home"
    const val SCAN = "scan"
    const val ABOUT = "about"
    const val SETTINGS = "settings"
}

@Composable
fun FreshNowNavHost(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = FreshNowRoute.HOME,
        // 转场期间两个页面同时处于半透明，这里垫上主题背景色，
        // 否则透出的是窗口背景（深色模式下偏亮，表现为一下白闪）
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
        enterTransition = { FreshNowTransitions.forwardEnter },
        exitTransition = { FreshNowTransitions.forwardExit },
        popEnterTransition = { FreshNowTransitions.backEnter },
        popExitTransition = { FreshNowTransitions.backExit }
    ) {
        composable(FreshNowRoute.HOME) {
            HomeRoute(
                onNavigateToScan = { navController.navigate(FreshNowRoute.SCAN) },
                onNavigateToAbout = { navController.navigate(FreshNowRoute.ABOUT) },
                onNavigateToSettings = { navController.navigate(FreshNowRoute.SETTINGS) }
            )
        }
        composable(FreshNowRoute.SCAN) {
            ScanScreen(onBack = { navController.navigateUp() })
        }
        composable(FreshNowRoute.ABOUT) {
            FreshNowSubPage(
                title = stringResource(R.string.about),
                onBack = { navController.navigateUp() }
            )
        }
        composable(FreshNowRoute.SETTINGS) {
            SettingsScreen(onBack = { navController.navigateUp() })
        }
    }
}
