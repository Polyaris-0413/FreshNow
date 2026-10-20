package com.freshnow.app.ui.about

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

/**
 * 依赖的 groupId 前缀 → 展示名，顺序即页面顺序。
 *
 * 只做归并，不做过滤：命中的按模块聚成一条（几十个 androidx.compose.* 归成「Jetpack Compose」
 * 一行），认不出的原样用 groupId 单独列一条。后一半是关键——漏一条就是署名缺失，而多一行只是
 * 难看。所以这份规则表可以不全，但绝不能把它当成「白名单」用。
 */
private val GROUP_RULES = listOf(
    "androidx.compose" to "Jetpack Compose",
    "androidx.camera" to "Android CameraX",
    "androidx.room" to "Android Room",
    "androidx.datastore" to "Android DataStore",
    "androidx." to "AndroidX",
    // 必须排在 "org.jetbrains.kotlin" 之前，否则 kotlinx.* 会被后面那条吃掉
    "org.jetbrains.kotlinx" to "Kotlin / kotlinx",
    "org.jetbrains.kotlin" to "Kotlin",
    "io.ktor" to "Ktor",
    "org.slf4j" to "SLF4J"
)

@Serializable
private data class Artifact(
    val groupId: String,
    val artifactId: String = "",
    val version: String = "",
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
 * 把 licensee 生成的 artifacts.json 归成页面上要显示的几条。
 *
 * 解析不了就返回空列表：这份文件是构建期打到 APK 里的，读不出来说明包有问题，不值得让整页崩掉。
 */
internal fun parseProjects(json: String): List<OpenSourceProject> {
    val artifacts = runCatching { licenseJson.decodeFromString<List<Artifact>>(json) }
        .getOrElse { return emptyList() }

    // 先按规则顺序建好桶，已知分组就固定排在前面；未命中的桶按出现顺序追加在后面
    val buckets = LinkedHashMap<String, MutableList<Artifact>>()
    GROUP_RULES.forEach { (_, label) -> buckets[label] = mutableListOf() }
    artifacts.forEach { artifact ->
        val label = GROUP_RULES.firstOrNull { artifact.groupId.startsWith(it.first) }?.second
            ?: artifact.groupId
        buckets.getOrPut(label) { mutableListOf() } += artifact
    }

    return buckets.filterValues { it.isNotEmpty() }.map { (label, group) ->
        val license = group
            .flatMap { artifact -> (artifact.spdxLicenses + artifact.unknownLicenses).map { it.name } }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" / ")
        // 组内任一依赖给出的仓库地址都行：同一个 group 下这些 artifact 的出处是同一处
        val url = group.firstNotNullOfOrNull { it.scm?.url?.takeIf(String::isNotBlank) }
            ?.removeSuffix(".git")
            .orEmpty()
        OpenSourceProject(name = label, license = license, url = url)
    }
}

/** licensee 打进 APK 的清单路径（见 app/build.gradle.kts 的 licensee.bundleAndroidAsset） */
private const val LICENSE_ASSET = "app/cash/licensee/artifacts.json"

private const val LICENSE_LOG_TAG = "FreshNowLicenses"

/**
 * 开源声明：列出用到的开源项目，点一行去项目主页。
 *
 * 数据不是手写的：构建期由 licensee 插件按真实依赖图生成 artifacts.json 并打进 assets，
 * 这一页只负责归并与展示。早先这里是一份手写常量表，引入依赖时没人记得同步（Ktor 与
 * kotlinx.serialization 就漏了 10 天），所以改成了现在这样——依赖变了清单自动跟着变。
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            projects.forEach { project ->
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
