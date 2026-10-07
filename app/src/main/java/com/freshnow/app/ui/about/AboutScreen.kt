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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.freshnow.app.BuildConfig
import com.freshnow.app.R
import com.freshnow.app.ui.component.FreshNowSubPage
import com.freshnow.app.ui.component.SectionHeading
import com.freshnow.app.ui.openInBrowser
import com.freshnow.app.ui.theme.FreshNowSpacing

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

    FreshNowSubPage(
        title = stringResource(R.string.about),
        onBack = onBack,
        modifier = modifier
    ) { innerPadding ->
        AboutList(
            onOpenUrl = { url -> openInBrowser(context, url) },
            onOpenSourceClick = onNavigateToOpenSource,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
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
    modifier: Modifier = Modifier
) {
    // 链接在合成期解析好：clickable 的 lambda 不是 composable，里面取不了资源
    val repositoryUrl = stringResource(R.string.project_repository_url)
    val issueUrl = stringResource(R.string.project_issue_url)

    Column(modifier = modifier) {
        // 法律声明放在最前：GPL-3.0 第 0 条要的是界面上有个「方便且显眼」的位置来说这四件事
        // ——版权、无担保、可依本协议转发、以及怎么看协议全文。它不属于任何分区，先于分区列出。
        Text(
            text = stringResource(R.string.legal_notice),
            modifier = Modifier.padding(
                start = FreshNowSpacing.sm,
                top = FreshNowSpacing.sm,
                end = FreshNowSpacing.sm
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SectionHeading(
            text = stringResource(R.string.app_settings_section_title),
            modifier = Modifier.padding(top = FreshNowSpacing.sm)
        )

            // 版本号放右端：它是这一行的「值」，不是对标题的说明。按 M3 列表项的解剖，说明在下
            // （supporting-text）、短值在右（trailing-supporting-text），这里没有要解释的东西，
            // 因此只给值、不给说明。
            // 这一行不可点：看完就知道版本，没有可做的动作，尾部也就不给箭头。
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
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            // 占位：检查更新尚未实现，所以这一行现在不接点击（接了也没有可做的事）。落地时要补的是
            // 检查中的状态、最新/有新版本的结果，以及「有新版本」时的去处。
            ListItem(
                leadingContent = {
                    Icon(painter = painterResource(R.drawable.ic_check_update), contentDescription = null)
                },
                headlineContent = {
                    Text(text = stringResource(R.string.app_settings_check_update_title))
                },
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
