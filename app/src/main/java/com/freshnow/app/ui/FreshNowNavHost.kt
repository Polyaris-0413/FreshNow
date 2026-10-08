package com.freshnow.app.ui

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.freshnow.app.R
import com.freshnow.app.ui.detail.RecordDetailScreen
import com.freshnow.app.ui.home.HomeRoute
import com.freshnow.app.ui.scan.ScanScreen
import com.freshnow.app.ui.settings.SettingsScreen
import com.freshnow.app.ui.about.AboutScreen
import com.freshnow.app.ui.about.OpenSourceScreen
import com.freshnow.app.ui.theme.FreshNowTransitions

object FreshNowRoute {
    const val HOME = "home"
    const val SCAN = "scan"
    const val ABOUT = "about"
    const val SETTINGS = "settings"
    const val OPEN_SOURCE = "openSource"

    const val RECORD_ID_ARG = "recordId"
    const val RECORD_DETAIL = "record/{$RECORD_ID_ARG}"

    fun recordDetail(id: Long) = "record/$id"
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
                onNavigateToSettings = { navController.navigate(FreshNowRoute.SETTINGS) },
                onNavigateToRecord = { id -> navController.navigate(FreshNowRoute.recordDetail(id)) }
            )
        }
        composable(FreshNowRoute.SCAN) {
            ScanScreen(
                onBack = { navController.navigateUp() },
                // 压栈而不是切换目的地：从设置回来仍落在扫描页，相机与累加记录都还在
                onNavigateToSettings = { navController.navigate(FreshNowRoute.SETTINGS) }
            )
        }
        composable(
            route = FreshNowRoute.RECORD_DETAIL,
            arguments = listOf(navArgument(FreshNowRoute.RECORD_ID_ARG) { type = NavType.LongType })
        ) { entry ->
            RecordDetailScreen(
                recordId = entry.arguments?.getLong(FreshNowRoute.RECORD_ID_ARG) ?: 0L,
                onBack = { navController.navigateUp() }
            )
        }
        composable(FreshNowRoute.ABOUT) {
            AboutScreen(
                onBack = { navController.navigateUp() },
                onNavigateToOpenSource = { navController.navigate(FreshNowRoute.OPEN_SOURCE) }
            )
        }
        composable(FreshNowRoute.SETTINGS) {
            SettingsScreen(onBack = { navController.navigateUp() })
        }
        composable(FreshNowRoute.OPEN_SOURCE) {
            OpenSourceScreen(onBack = { navController.navigateUp() })
        }
    }
}
