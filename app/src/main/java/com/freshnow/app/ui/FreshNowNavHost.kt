package com.freshnow.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.freshnow.app.R
import com.freshnow.app.ui.component.FreshNowSubPage
import com.freshnow.app.ui.home.HomeScreen

object FreshNowRoute {
    const val HOME = "home"
    const val ABOUT = "about"
    const val SETTINGS = "settings"
}

@Composable
fun FreshNowNavHost(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = FreshNowRoute.HOME
    ) {
        composable(FreshNowRoute.HOME) {
            HomeScreen(
                onNavigateToAbout = { navController.navigate(FreshNowRoute.ABOUT) },
                onNavigateToSettings = { navController.navigate(FreshNowRoute.SETTINGS) }
            )
        }
        composable(FreshNowRoute.ABOUT) {
            FreshNowSubPage(
                title = stringResource(R.string.about),
                onBack = { navController.navigateUp() }
            )
        }
        composable(FreshNowRoute.SETTINGS) {
            FreshNowSubPage(
                title = stringResource(R.string.settings),
                onBack = { navController.navigateUp() }
            )
        }
    }
}
