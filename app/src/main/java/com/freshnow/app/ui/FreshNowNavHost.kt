package com.freshnow.app.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.freshnow.app.ui.detail.RecordDetailScreen
import com.freshnow.app.ui.detail.RecordEditScreen
import com.freshnow.app.ui.home.HomeRoute
import com.freshnow.app.ui.scan.ScanScreen
import com.freshnow.app.ui.settings.SettingsScreen
import com.freshnow.app.ui.sync.SyncScreen
import com.freshnow.app.ui.about.AboutScreen
import com.freshnow.app.ui.about.OpenSourceScreen
import com.freshnow.app.ui.theme.FreshNowTransitions
import com.freshnow.app.ui.theme.backEnter
import com.freshnow.app.ui.theme.backExit
import com.freshnow.app.ui.theme.forwardEnter
import com.freshnow.app.ui.theme.forwardExit

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

/**
 * 从属于某一页的子页：进这些页与从这些页返回走共享轴——横向位移本身在说「这两个页面一前一后」。
 *
 * 判据是「这一页是不是某个页面的下级」，而不是「从哪儿来」：同步是设置的子页，详情与编辑是主页
 * 那一行的下级，开源声明是关于的下级。扫描、设置、关于、手动录入都是顶层去处，它们之间（以及
 * 它们与主页之间）没有前后关系，走淡入淡出——给平行去处配横向位移，等于在说一句假话。
 *
 * 【这份分类是本项目的判断，不是规范写的】M3 只给原则（有空间/导航关系的用 shared axis、
 * 没有的用 fade through），哪一页属于哪一类得自己判。改这个集合不违反规范，只是把某一页的说法
 * 换个位置而已。
 */
private val CHILD_DESTINATIONS = setOf(
    FreshNowRoute.RECORD_DETAIL,
    FreshNowRoute.RECORD_EDIT,
    FreshNowRoute.SYNC,
    FreshNowRoute.OPEN_SOURCE
)

/** 一程的两端只要有一端是子页，这一程就是层级跳转 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isChildJump(): Boolean =
    initialState.destination.route in CHILD_DESTINATIONS ||
        targetState.destination.route in CHILD_DESTINATIONS

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
        enterTransition = { if (isChildJump()) forwardEnter() else FreshNowTransitions.fadeThroughEnter },
        exitTransition = { if (isChildJump()) forwardExit() else FreshNowTransitions.fadeThroughExit },
        popEnterTransition = { if (isChildJump()) backEnter() else FreshNowTransitions.fadeThroughEnter },
        popExitTransition = { if (isChildJump()) backExit() else FreshNowTransitions.fadeThroughExit }
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
                onNavigateToSettings = { navigator.navigate(FreshNowRoute.SETTINGS) },
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
