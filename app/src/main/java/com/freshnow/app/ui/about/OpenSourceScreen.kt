package com.freshnow.app.ui.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.freshnow.app.R
import com.freshnow.app.ui.component.FreshNowSubPage
import com.freshnow.app.ui.openInBrowser
import com.freshnow.app.ui.theme.FreshNowSpacing

/**
 * 用到的开源项目，点一行去项目主页。项目名与许可名都是专有名词，不进 strings.xml。
 *
 * 页面这里只列清单，不铺许可全文：GPL-3.0 第 4 条要的是「随程序附一份副本」，副本的载体是
 * 分发物（本应用仓库的 LICENSE），并没说要在界面上逐字显示；界面那条（第 0 条）要的是告诉
 * 用户「怎么看这份副本」，而每一行点开就是该项目的仓库与许可原文。
 */
private data class OpenSourceProject(
    val name: String,
    val license: String,
    val url: String
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
    OpenSourceProject("book-story", GPL, "https://github.com/Acclorite/book-story")
)

/**
 * 开源声明：列出用到的开源项目。
 *
 * 这份清单是给那几个 Apache-2.0 依赖用的——该协议要求分发时保留它们的版权与署名，惯例就是
 * 这样列出来。book-story 是 GPL-3.0，它那边的义务不在这里：改过的声明与日期写在仓库的
 * README/源码头（第 5a、5b 条），界面上那一份法律声明在关于页（第 0 条）。
 *
 * 没有用 OSS licenses 那类插件自动生成：它们靠依赖的 POM 出清单，覆盖不到「抄进来的源码」——
 * 本项目确实用到了 book-story 的源码，它必须由人来写清楚。
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
            PROJECTS.forEach { project ->
                ListItem(
                    headlineContent = { Text(text = project.name) },
                    supportingContent = { Text(text = project.license) },
                    modifier = Modifier.clickable { openInBrowser(context, project.url) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}
