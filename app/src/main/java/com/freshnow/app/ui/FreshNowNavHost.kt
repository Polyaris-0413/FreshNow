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
import com.freshnow.app.ui.detail.RecordEditScreen
import com.freshnow.app.ui.home.HomeRoute
import com.freshnow.app.ui.scan.ScanScreen
import com.freshnow.app.ui.settings.SettingsScreen
import com.freshnow.app.ui.sync.SyncScreen
import com.freshnow.app.ui.about.AboutScreen
import com.freshnow.app.ui.about.OpenSourceScreen
import com.freshnow.app.ui.theme.FreshNowTransitions

object FreshNowRoute {
    const val HOME = "home"
    const val SCAN = "scan"
    const val ABOUT = "about"
    const val SETTINGS = "settings"
    const val SYNC = "sync"
    const val OPEN_SOURCE = "openSource"

    /**
     * 手动录入（行为开关开着时的「添加」）。
     *
     * 不是 `record/new`：那条路会被 `record/{recordId}` 当成一个 id 去解析（NavType.LongType），
     * 对不上就会在导航时抛异常；这一页本来也不是「某条记录」，另起一个平级名字更清楚。
     */
    const val MANUAL_ENTRY = "manualEntry"

    const val RECORD_ID_ARG = "recordId"
    const val RECORD_DETAIL = "record/{$RECORD_ID_ARG}"
    const val RECORD_EDIT = "record/{$RECORD_ID_ARG}/edit"

    fun recordDetail(id: Long) = "record/$id"

    fun recordEdit(id: Long) = "record/$id/edit"
}

@Composable
fun FreshNowNavHost(
    navController: NavHostController = rememberNavController()
) {
    // 页面之间的跳转一律走这里，而不是直接调 navController：转场没演完就再出发会把动画挤掉，
    // 表现为硬切加一下黑闪（见 SettledNavigator）
    val navigator = rememberSettledNavigator(navController)

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
                onNavigateToScan = { navigator.navigate(FreshNowRoute.SCAN) },
                onNavigateToManualEntry = { navigator.navigate(FreshNowRoute.MANUAL_ENTRY) },
                onNavigateToAbout = { navigator.navigate(FreshNowRoute.ABOUT) },
                onNavigateToSettings = { navigator.navigate(FreshNowRoute.SETTINGS) },
                onNavigateToRecord = { id -> navigator.navigate(FreshNowRoute.recordDetail(id)) }
            )
        }
        // 手动录入：库里还没有这条记录，recordId 给 null；保存之后保存按钮那边会退回主页
        composable(FreshNowRoute.MANUAL_ENTRY) {
            RecordEditScreen(
                recordId = null,
                onBack = { navigator.navigateUp() }
            )
        }
        composable(FreshNowRoute.SCAN) {
            ScanScreen(
                onBack = { navigator.navigateUp() },
                // 压栈而不是切换目的地：从设置回来仍落在扫描页，相机与累加记录都还在
                onNavigateToSettings = { navigator.navigate(FreshNowRoute.SETTINGS) }
            )
        }
        composable(
            route = FreshNowRoute.RECORD_DETAIL,
            arguments = listOf(navArgument(FreshNowRoute.RECORD_ID_ARG) { type = NavType.LongType })
        ) { entry ->
            val recordId = entry.arguments?.getLong(FreshNowRoute.RECORD_ID_ARG) ?: 0L
            RecordDetailScreen(
                recordId = recordId,
                onBack = { navigator.navigateUp() },
                onNavigateToEdit = { navigator.navigate(FreshNowRoute.recordEdit(recordId)) }
            )
        }
        composable(
            route = FreshNowRoute.RECORD_EDIT,
            arguments = listOf(navArgument(FreshNowRoute.RECORD_ID_ARG) { type = NavType.LongType })
        ) { entry ->
            RecordEditScreen(
                recordId = entry.arguments?.getLong(FreshNowRoute.RECORD_ID_ARG) ?: 0L,
                onBack = { navigator.navigateUp() }
            )
        }
        composable(FreshNowRoute.ABOUT) {
            AboutScreen(
                onBack = { navigator.navigateUp() },
                onNavigateToOpenSource = { navigator.navigate(FreshNowRoute.OPEN_SOURCE) }
            )
        }
        composable(FreshNowRoute.SETTINGS) {
            SettingsScreen(
                onBack = { navigator.navigateUp() },
                onNavigateToSync = { navigator.navigate(FreshNowRoute.SYNC) }
            )
        }
        composable(FreshNowRoute.SYNC) {
            SyncScreen(onBack = { navigator.navigateUp() })
        }
        composable(FreshNowRoute.OPEN_SOURCE) {
            OpenSourceScreen(onBack = { navigator.navigateUp() })
        }
    }
}
