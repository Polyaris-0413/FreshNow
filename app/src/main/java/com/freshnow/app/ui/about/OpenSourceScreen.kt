package com.freshnow.app.ui.about

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.freshnow.app.R
import com.freshnow.app.ui.component.FreshNowSubPage
import com.freshnow.app.ui.component.ListItemText
import com.freshnow.app.ui.openInBrowser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 一个要署名的开源项目。项目名与协议名都是专有名词，不进 strings.xml。
 */
internal data class OpenSourceProject(
    val name: String,
    val license: String,
    val url: String
)

@Serializable
private data class Artifact(
    val groupId: String,
    val artifactId: String = "",
    val version: String = "",
    /** POM 里的 <name>，没有时为空串 */
    val name: String = "",
    val spdxLicenses: List<LicenseEntry> = emptyList(),
    /** 在 POM 里只写了名字和 URL、对不上 SPDX 的协议（如 SLF4J 的 MIT），licensee 归到这一栏 */
    val unknownLicenses: List<LicenseEntry> = emptyList(),
    val scm: Scm? = null
)

@Serializable
private data class LicenseEntry(
    val identifier: String = "",
    val name: String = "",
    val url: String = ""
)

@Serializable
private data class Scm(val url: String = "")

private val licenseJson = Json { ignoreUnknownKeys = true }

/**
 * 把 licensee 生成的 artifacts.json 逐条摊成页面上的一行。
 *
 * 一个依赖一行，不做任何归并：归并规则（把 androidx.compose.* 折成「Jetpack Compose」那种）
 * 本来就是手写清单的替代品，会随依赖变化而失真——少归一条就露出 groupId，归错就把不同的库
 * 说成同一个。逐条摊开之后，页面上有的东西与依赖图里的东西一一对应，没有中间层可错。
 *
 * 代价是长：几百个依赖就是几百行，所以页面用 LazyColumn 而不是一次性组合。
 *
 * 解析不了就返回空列表：这份文件是构建期打到 APK 里的，读不出来说明包有问题，不值得让整页崩掉。
 */
internal fun parseProjects(json: String): List<OpenSourceProject> {
    val artifacts = runCatching { licenseJson.decodeFromString<List<Artifact>>(json) }
        .getOrElse { return emptyList() }

    return artifacts.map { artifact ->
        OpenSourceProject(
            // POM 里没写 <name> 时退回坐标，总比空一行强
            name = artifact.name.ifBlank { "${artifact.groupId}:${artifact.artifactId}" },
            license = (artifact.spdxLicenses + artifact.unknownLicenses)
                .map { it.name }
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(" / "),
            url = artifact.scm?.url?.takeIf(String::isNotBlank)?.removeSuffix(".git").orEmpty()
        )
    }
}

/** licensee 打进 APK 的清单路径（见 app/build.gradle.kts 的 licensee.bundleAndroidAsset） */
private const val LICENSE_ASSET = "app/cash/licensee/artifacts.json"

private const val LICENSE_LOG_TAG = "FreshNowLicenses"

/**
 * 开源声明：列出用到的每一个第三方依赖，点一行去它的仓库。
 *
 * 数据不是手写的：构建期由 licensee 插件按真实依赖图生成 artifacts.json 并打进 assets，
 * 这一页只负责摊开与展示。早先这里是一份手写常量表，引入依赖时没人记得同步（Ktor 与
 * kotlinx.serialization 就漏了 10 天），改完第一版又留了一组手写的归并规则；现在连规则也去掉了，
 * 依赖图变则页面变，中间没有需要人维护的东西。
 *
 * 列的是第三方依赖，给 Apache-2.0 那批做署名——该协议要求分发时保留版权与许可声明，惯例就是
 * 这样列出来。页面只列清单、不铺许可全文：全文的载体是分发物（本仓库的 LICENSE），摊在手机上
 * 要滚很久，也挤掉了这一页本身要传达的信息。
 */
@Composable
fun OpenSourceScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val projects by produceState(initialValue = emptyList<OpenSourceProject>(), context) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(LICENSE_ASSET).bufferedReader().use { parseProjects(it.readText()) }
            }.getOrElse { error ->
                Log.w(LICENSE_LOG_TAG, "开源清单读不出来（$LICENSE_ASSET）", error)
                emptyList()
            }
        }
    }

    FreshNowSubPage(
        title = stringResource(R.string.legal_open_source_title),
        onBack = onBack,
        modifier = modifier
    ) { innerPadding ->
        // 几百行一次组合会明显拖慢进页：只在需要时组合
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // 条目滚到顶栏下面时顶栏才变色（见 FreshNowTopAppBar），视口因此要铺满整个 Scaffold
            contentPadding = innerPadding
        ) {
            items(projects) { project ->
                ListItem(
                    headlineContent = { ListItemText(project.name, project.license) },
                    modifier = if (project.url.isEmpty()) {
                        Modifier
                    } else {
                        Modifier.clickable { openInBrowser(context, project.url) }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}
