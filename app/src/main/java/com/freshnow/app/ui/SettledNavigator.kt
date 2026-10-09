package com.freshnow.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 转场还没演完就出发下一次导航，NavHost 会把正在演的那一次扔掉。
 *
 * 出处是 NavHost 的 transitionSpec：AnimatedContent 的初始内容若已经不在 visibleEntries 里，
 * 它按「旧的那一页已经被清掉了」处理，直接换成 EnterTransition.None / ExitTransition.None
 * （navigation-compose 2.10.2，NavHost.kt 里那段带注释的 `initialState in visibleEntries`）。
 * 于是旧的整屏合成被拆掉、新的还没上来，中间几帧只剩 NavHost 垫着的主题底色——就是那一下黑闪
 * （见 FreshNowNavHost 里给 NavHost 垫的那层 background），随后新页面硬切上来，动画没了。
 *
 * 最容易撞上的是「回上一页还没落定，就再点一次前进」：返回时上一页是**淡入过程中**就已经可点的
 * （详情页的「编辑」、编辑页的「保存」都在这条路上），手快的人正好落在这一程里。
 *
 * 「演完了没有」有现成的判据，不必抄一遍时长：转场期间顶层那条记录的 lifecycle 被压在 STARTED，
 * NavHost 标记这次转场完成时才升到 RESUMED（见 NavController 的 markTransitionComplete 与
 * updateBackStackLifecycle）。所以这里等的是那条记录真的 RESUMED。
 *
 * 晚出发而不是把这一下丢掉：点击是用户真想要的，落地晚几百毫秒而已。但同一程里只认第一下——
 * 几百毫秒内的第二下几乎总是同一次连点，排队会让「点两下前进」变成前进两页。
 *
 * 只覆盖应用自己的入口（顶栏、按钮、列表行）；系统返回键由 NavHost 内部处理，不走这里。
 */
@Stable
class SettledNavigator internal constructor(
    private val navController: NavHostController,
    private val scope: CoroutineScope
) {

    /** 去 [route]；上一次转场若还没演完，等它收尾再走 */
    fun navigate(route: String) = navigateWhenSettled { navController.navigate(route) }

    /** 退回上一页；同上 */
    fun navigateUp() = navigateWhenSettled { navController.navigateUp() }

    private fun navigateWhenSettled(action: () -> Unit) {
        // 常规情况：没有转场在演，立刻出发，不进任何队列
        if (!isTransitioning()) {
            action()
            return
        }
        if (pending) return
        pending = true
        scope.launch {
            try {
                awaitSettled()
                action()
            } finally {
                // 协程被取消（本页合成没了）时也要放开，否则这一次以后再也导航不了
                pending = false
            }
        }
    }

    /** 是否有一程正在演：顶层那条记录还没到 RESUMED 就说明它还在台上 */
    private fun isTransitioning(): Boolean =
        navController.currentBackStackEntry
            ?.lifecycle
            ?.currentState
            ?.isAtLeast(Lifecycle.State.RESUMED) == false

    /** 等到顶层那条记录 RESUMED。等的是事件，不是时长 */
    private suspend fun awaitSettled() {
        while (true) {
            val entry = navController.currentBackStackEntry ?: return
            if (entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
            suspendCancellableCoroutine { continuation ->
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) continuation.resume(Unit)
                }
                entry.lifecycle.addObserver(observer)
                continuation.invokeOnCancellation { entry.lifecycle.removeObserver(observer) }
            }
        }
    }

    private var pending = false
}

@Composable
fun rememberSettledNavigator(navController: NavHostController): SettledNavigator {
    val scope = rememberCoroutineScope()
    return remember(navController, scope) { SettledNavigator(navController, scope) }
}
