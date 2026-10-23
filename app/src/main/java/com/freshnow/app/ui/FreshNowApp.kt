package com.freshnow.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.freshnow.app.data.OnboardingRepository
import com.freshnow.app.ui.theme.FreshNowTransitions
import com.freshnow.app.ui.welcome.WelcomeScreen
import kotlinx.coroutines.flow.first

/**
 * 「有没有看过欢迎页」是异步读出来的，读完之前两边都不渲染，只铺 colorScheme.background（先渲染主页再切走，用户会看到主页闪一下）——
 * 它与启动图、窗口底色是同一个值（见 FreshNowTheme），于是从启动图到第一帧没有色差。
 * 首次检查更新挂在这里而不是 MainActivity：它要在进了主页之后才开始查，否则对话框会盖在欢迎页上。
 */
@Composable
fun FreshNowApp() {
    val context = LocalContext.current
    val onboardingRepository = remember(context) { OnboardingRepository(context) }
    var welcomeSeen by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(onboardingRepository) {
        welcomeSeen = onboardingRepository.welcomeSeen.first()
    }

    AnimatedContent(
        targetState = welcomeSeen,
        transitionSpec = { FreshNowTransitions.fadeSwap() },
        modifier = Modifier.fillMaxSize()
    ) { seen ->
        when (seen) {
            null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            )

            false -> WelcomeScreen(onFinish = { welcomeSeen = true })

            true -> {
                FreshNowNavHost()
                // 对话框盖在所有页面之上，与当前停在哪一页无关，所以不必进每个页面各接一次
                AutoUpdateCheck()
            }
        }
    }
}
