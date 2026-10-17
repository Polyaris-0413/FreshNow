package com.freshnow.app.ui.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.freshnow.app.R
import com.freshnow.app.ui.component.FreshNowSubPage
import com.freshnow.app.ui.component.ListItemText
import com.freshnow.app.ui.openInBrowser

/**
 * 用到的开源项目，点一行去项目主页。项目名与许可名都是专有名词，不进 strings.xml。
 *
 * 列的是第三方依赖，给 Apache-2.0 那批做署名——该协议要求分发时保留版权与许可声明，惯例就是
 * 这样列出来。页面只列清单、不铺许可全文：全文的载体是分发物（本仓库的 LICENSE），摊在手机上
 * 要滚很久，也挤掉了这一页本身要传达的信息。
 */
private data class OpenSourceProject(
    val name: String,
    val license: String,
    val url: String
)

private const val APACHE = "Apache License 2.0"

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
    )
)

/**
 * 开源声明：列出用到的开源项目。
 *
 * 这份清单是给那几个 Apache-2.0 依赖用的——该协议要求分发时保留它们的版权与署名，惯例就是
 * 这样列出来。没有用 OSS licenses 那类插件自动生成：它们只在构建时按 POM 出清单，版式与分组
 * 由不了这一页（这一页还要跟着 strings.xml 走）。
 *
 * 早先这里还有一行界面动效的参考项目（GPL-3.0）。那几条页面转场已按 M3 的 motion 规范重写
 * （shared axis 与 fade through，取值出自 M3 令牌表），项目里不再有它的代码，那行署名也随之撤掉。
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
                    headlineContent = { ListItemText(project.name, project.license) },
                    modifier = Modifier.clickable { openInBrowser(context, project.url) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}
