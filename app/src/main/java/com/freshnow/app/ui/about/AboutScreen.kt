package com.freshnow.app.ui.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.freshnow.app.BuildConfig
import com.freshnow.app.R
import com.freshnow.app.data.LatestRelease
import com.freshnow.app.data.UpdateChecker
import com.freshnow.app.data.UpdateCheckResult
import com.freshnow.app.data.compareVersions
import com.freshnow.app.ui.component.FreshNowSubPage
import com.freshnow.app.ui.component.SectionHeading
import com.freshnow.app.ui.component.UpdateDialog
import com.freshnow.app.ui.openInBrowser
import com.freshnow.app.ui.showToast
import com.freshnow.app.ui.theme.FreshNowSpacing
import kotlinx.coroutines.launch

/**
 * 关于：这个应用是什么、出处在哪。可调项不在这里，在设置页。
 *
 * 三个分区：应用（版本、检查更新）、项目（仓库、问题反馈）、法律信息（开源声明）。
 * 版本号是「值」，放标题右端；说明性的文字才放下面——见各行的注释。
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onNavigateToOpenSource: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Toast 要的是结果字符串，在合成期解析好
    val versionToast = stringResource(R.string.app_settings_version_toast)
    val checkingToast = stringResource(R.string.update_checking)
    val latestToast = stringResource(R.string.update_latest)
    val failedToast = stringResource(R.string.update_check_failed)

    val scope = rememberCoroutineScope()
    val updateChecker = remember { UpdateChecker() }
    var update by remember { mutableStateOf<LatestRelease?>(null) }

    /**
     * 手动检查更新。三条出路各说一句：有新版本 → 弹对话框；没新版（含仓库还没发过版本）→
     * 「已是最新版本」；没查成 → 一句提示。
     *
     * 先报一声「正在检查更新…」：这一查要联网，快则一两秒、慢则十秒，不给提示的话按下去
     * 会以为按钮没反应。
     *
     * 手动检查不记「不再提醒」：那个语义只属于自动检查（用户没主动要查，弹出来算打扰），
     * 主动查过的人给的是「我要看」这个意思。
     */
    fun checkUpdate() {
        showToast(context, checkingToast)
        scope.launch {
            when (val result = updateChecker.checkLatest()) {
                is UpdateCheckResult.Latest -> {
                    // 版本号以编进包里的为准：包里的那份才是用户手上这一份
                    if (compareVersions(result.release.version, BuildConfig.VERSION_NAME) > 0) {
                        update = result.release
                    } else {
                        showToast(context, latestToast)
                    }
                }

                UpdateCheckResult.NoRelease -> showToast(context, latestToast)
                UpdateCheckResult.Failed -> showToast(context, failedToast)
            }
        }
    }

    FreshNowSubPage(
        title = stringResource(R.string.about),
        onBack = onBack,
        modifier = modifier
    ) { innerPadding ->
        AboutList(
            onOpenUrl = { url -> openInBrowser(context, url) },
            onOpenSourceClick = onNavigateToOpenSource,
            onVersionClick = { showToast(context, versionToast) },
            onCheckUpdateClick = { checkUpdate() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        )
    }

    update?.let { release ->
        UpdateDialog(
            version = release.version,
            onDownload = {
                update = null
                openInBrowser(context, release.htmlUrl)
            },
            onDismiss = { update = null }
        )
    }
}

/**
 * 关于页的正文：三个分区与各行。不含导航与浏览器——那两件事由 [AboutScreen] 接上，
 * 于是这里可以按定值直接断言（见 AboutScreenTest），不必去验 intent。
 */
@Composable
internal fun AboutList(
    onOpenUrl: (String) -> Unit,
    onOpenSourceClick: () -> Unit,
    onVersionClick: () -> Unit,
    onCheckUpdateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 链接在合成期解析好：clickable 的 lambda 不是 composable，里面取不了资源
    val repositoryUrl = stringResource(R.string.project_repository_url)
    val issueUrl = stringResource(R.string.project_issue_url)

    Column(modifier = modifier) {
        SectionHeading(
        text = stringResource(R.string.app_settings_section_title),
        // 与页面顶端的距离
        modifier = Modifier.padding(top = FreshNowSpacing.sm)
        )

        // 版本号放右端：它是这一行的「值」，不是对标题的说明。按 M3 列表项的解剖，说明在下
        // （supporting-text）、短值在右（trailing-supporting-text），这里没有要解释的东西，
        // 因此只给值、不给说明。
        //
        // 点它是彩蛋：弹一个日期。同页其余各行都可点，这一行也不该按下去毫无反应。
        ListItem(
            leadingContent = {
                Icon(painter = painterResource(R.drawable.ic_version), contentDescription = null)
            },
            headlineContent = {
                Text(text = stringResource(R.string.app_settings_version_title))
            },
            trailingContent = {
                Text(
                    text = BuildConfig.VERSION_NAME,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            modifier = Modifier.clickable(onClick = onVersionClick),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )

        // 检查更新：查 GitHub 上最新的 release。查成且有新版才弹对话框，其余两种情况各给一句
        // 提示（见 AboutScreen.checkUpdate），本行的形态与其余各行一致
        ListItem(
            leadingContent = {
                Icon(painter = painterResource(R.drawable.ic_check_update), contentDescription = null)
            },
            headlineContent = {
                Text(text = stringResource(R.string.app_settings_check_update_title))
            },
            modifier = Modifier.clickable(onClick = onCheckUpdateClick),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )

        SectionHeading(
            text = stringResource(R.string.project_settings_section_title),
            // 与上一段之间留段间距（设计源的 24），分区才分得开
            modifier = Modifier.padding(top = FreshNowSpacing.md)
        )

        // 两行都去浏览器：链接从资源取，行本身不知道具体地址
        ListItem(
            leadingContent = {
                Icon(painter = painterResource(R.drawable.ic_repository), contentDescription = null)
            },
            headlineContent = {
                Text(text = stringResource(R.string.project_repository_title))
            },
            modifier = Modifier.clickable { onOpenUrl(repositoryUrl) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )

        ListItem(
            leadingContent = {
                Icon(painter = painterResource(R.drawable.ic_issue), contentDescription = null)
            },
            headlineContent = {
                Text(text = stringResource(R.string.project_issue_title))
            },
            modifier = Modifier.clickable { onOpenUrl(issueUrl) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )

        SectionHeading(
            text = stringResource(R.string.legal_settings_section_title),
            modifier = Modifier.padding(top = FreshNowSpacing.md)
        )

        ListItem(
            leadingContent = {
                Icon(painter = painterResource(R.drawable.ic_open_source), contentDescription = null)
            },
            headlineContent = {
                Text(text = stringResource(R.string.legal_open_source_title))
            },
            modifier = Modifier.clickable(onClick = onOpenSourceClick),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}
