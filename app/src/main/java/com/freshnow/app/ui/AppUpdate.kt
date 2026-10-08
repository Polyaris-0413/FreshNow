package com.freshnow.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.freshnow.app.BuildConfig
import com.freshnow.app.R
import com.freshnow.app.data.LatestRelease
import com.freshnow.app.data.UpdateChecker
import com.freshnow.app.data.UpdateCheckResult
import com.freshnow.app.data.UpdateSettingsRepository
import com.freshnow.app.data.shouldPromptUpdate
import com.freshnow.app.ui.component.UpdateDialog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 冷启动自动检查更新：查一次，有新版本且没被「本次不再提醒」忽略过就弹对话框。
 *
 * 只在冷启动查这一次，不挂定时器、也不在回到前台时再查：这是个看心情用的应用，隔一会儿弹一次
 * 更新提示只会招人烦，而启动那一次已经覆盖了「用户又打开它了」这个时机。
 *
 * 「关闭」记住的是这一个版本（见 UpdateSettingsRepository）：下一个版本仍要提示。
 *
 * 挂在整个导航之外（见 MainActivity）：对话框是覆盖在所有页面之上的，与当前停在哪一页无关。
 */
@Composable
fun AutoUpdateCheck(checker: UpdateChecker = remember { UpdateChecker() }) {
    val context = LocalContext.current
    val settings = remember { UpdateSettingsRepository(context.applicationContext) }
    val dismissedToast = stringResource(R.string.update_dismissed)
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<LatestRelease?>(null) }

    LaunchedEffect(Unit) {
        val result = checker.checkLatest()
        if (result !is UpdateCheckResult.Latest) return@LaunchedEffect
        val release = result.release
        // 该不该提示整个判据在 data 层（见 shouldPromptUpdate），界面只负责拿结果
        if (shouldPromptUpdate(release, BuildConfig.VERSION_NAME, settings.ignoredVersion.first())) {
            pending = release
        }
    }

    pending?.let { release ->
        UpdateDialog(
            version = release.version,
            onDownload = {
                pending = null
                // 下载交给浏览器：应用内自己下载安装要申请「安装未知应用」权限，还得处理下载失败
                // 与校验，而发布页本身就能下，为一个单一产物多要一项权限不成比例
                openInBrowser(context, release.htmlUrl)
            },
            onDismiss = {
                pending = null
                scope.launch { settings.setIgnoredVersion(release.version) }
                // 记下了什么要回一句：用户下次不会再看到这个版本的提示，不说的话不知道自己关掉了什么
                showToast(context, dismissedToast)
            }
        )
    }
}
