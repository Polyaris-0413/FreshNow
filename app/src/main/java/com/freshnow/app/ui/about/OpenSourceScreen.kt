package com.freshnow.app.ui.about

import androidx.annotation.RawRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.freshnow.app.R
import com.freshnow.app.ui.component.FreshNowSubPage
import com.freshnow.app.ui.component.SectionHeading
import com.freshnow.app.ui.openInBrowser
import com.freshnow.app.ui.theme.FreshNowSpacing

/**
 * 用到的开源项目。项目名与许可名都是专有名词，不进 strings.xml。
 *
 * 许可只写名称，全文在页面下半部分列出——GPL-3.0 要求随程序附一份许可副本，指向官网链接
 * 不算附副本。
 */
private data class OpenSourceProject(
    val name: String,
    val license: String,
    val url: String,
    /**
     * 本应用的实现取自该项目。GPL-3.0 第 5 条要求衍生作品写明这一点并给出改动日期，
     * 改动逐条记在本应用仓库的提交记录里，页面上也照此写。
     */
    val derived: Boolean = false
)

private const val APACHE = "Apache License 2.0"
private const val GPL = "GNU General Public License v3.0"

private val PROJECTS = listOf(
    OpenSourceProject("Kotlin / kotlinx.coroutines", APACHE, "https://kotlinlang.org"),
    OpenSourceProject(
        "Jetpack Compose（androidx.compose.*）",
        APACHE,
        "https://developer.android.com/jetpack/compose"
    ),
    OpenSourceProject(
        "AndroidX（core / lifecycle / activity / navigation / datastore）",
        APACHE,
        "https://developer.android.com/jetpack/androidx"
    ),
    OpenSourceProject("Android Room", APACHE, "https://developer.android.com/training/data-storage/room"),
    OpenSourceProject(
        "Android CameraX",
        APACHE,
        "https://developer.android.com/media/camera/camerax"
    ),
    OpenSourceProject("book-story", GPL, "https://github.com/Acclorite/book-story", derived = true)
)

/**
 * 开源声明：列出用到的开源项目，并附许可全文。
 *
 * 顺带说明这里没有用 OSS licenses 类的插件自动生成：那类方案靠依赖的 POM 生成清单，
 * 覆盖不到「抄进来的源码」——本项目确实用到了 book-story 的源码，它必须由人来写清楚。
 */
@Composable
fun OpenSourceScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    FreshNowSubPage(
        title = stringResource(R.string.legal_open_source_title),
        onBack = onBack,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.open_source_intro),
                modifier = Modifier.padding(
                    start = FreshNowSpacing.sm,
                    top = FreshNowSpacing.sm,
                    end = FreshNowSpacing.sm
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            PROJECTS.forEach { project ->
                ListItem(
                    headlineContent = { Text(text = project.name) },
                    supportingContent = { Text(text = project.license) },
                    modifier = Modifier.clickable { openInBrowser(context, project.url) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                if (project.derived) {
                    // 取自该项目的部分要写明出处与改动去处（GPL-3.0 第 5 条）
                    Text(
                        text = stringResource(R.string.open_source_derived_notice),
                        modifier = Modifier.padding(
                            start = FreshNowSpacing.sm,
                            end = FreshNowSpacing.sm,
                            bottom = FreshNowSpacing.xs
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SectionHeading(
                text = stringResource(R.string.open_source_licenses_title),
                modifier = Modifier.padding(top = FreshNowSpacing.md)
            )

            LicenseText(
                heading = stringResource(R.string.open_source_apache_license),
                resource = R.raw.license_apache_2_0
            )
            LicenseText(
                heading = stringResource(R.string.open_source_gpl_license),
                resource = R.raw.license_gpl_3_0,
                dividerAbove = true
            )
        }
    }
}

/**
 * 一段许可全文。
 *
 * 文本直接从 raw 资源同步读：两份加起来不到 900 行（几十 KB），读一次的耗时远小于一帧，
 * 为它铺一条异步管线不值得。
 */
@Composable
private fun LicenseText(
    heading: String,
    @RawRes resource: Int,
    modifier: Modifier = Modifier,
    dividerAbove: Boolean = false
) {
    val context = LocalContext.current
    val text = remember(resource) {
        context.resources.openRawResource(resource).bufferedReader().use { it.readText() }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (dividerAbove) {
            HorizontalDivider(modifier = Modifier.padding(vertical = FreshNowSpacing.sm))
        }
        Text(
            text = heading,
            modifier = Modifier.padding(horizontal = FreshNowSpacing.sm),
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = text,
            modifier = Modifier.padding(
                start = FreshNowSpacing.sm,
                top = FreshNowSpacing.xs,
                end = FreshNowSpacing.sm,
                bottom = FreshNowSpacing.md
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
