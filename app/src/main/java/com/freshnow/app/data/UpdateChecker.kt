package com.freshnow.app.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection

/**
 * 从仓库页地址（strings.xml 的 project_repository_url / project_repository_url_gitee）取出仓库标识 owner/name。
 *
 * 不把仓库标识另存一份：那样「访问仓库」与「检查更新」就有两个来源，改仓库时漏一处，
 * 而漏掉的表现是「界面上的仓库是对的、检查更新却查不到东西」。主机名从 [source] 取，
 * 两个平台的仓库路径写法相同但域名不同，一个写死的域名会把另一个源的地址切出空 slug。
 * 不是该源地址时返回空串：那样接口会 404，由调用方按「这次没查成」处理，
 * 而不会把整条地址拼进接口路径里当成 owner/name。
 */
internal fun repositorySlug(source: UpdateSource, repositoryUrl: String): String =
    trimmedRepositoryUrl(repositoryUrl).substringAfter("${source.host}/", missingDelimiterValue = "")

internal fun latestReleaseApiUrl(source: UpdateSource, repositoryUrl: String): String =
    "${source.apiPrefix}${repositorySlug(source, repositoryUrl)}/releases/latest"

/** 接口没给 html_url 时退回这里，两条路都是同一页（两个平台的 /releases/latest 都可用） */
internal fun releasesPageUrl(repositoryUrl: String): String =
    "${trimmedRepositoryUrl(repositoryUrl)}/releases/latest"

/** 去掉首尾空白、末尾斜杠与 .git 后缀，网页地址与 clone 地址因此推出同一结果 */
private fun trimmedRepositoryUrl(repositoryUrl: String): String =
    repositoryUrl.trim().trimEnd('/').removeSuffix(".git")

private const val CONNECT_TIMEOUT_MS = 10_000
private const val READ_TIMEOUT_MS = 10_000

data class LatestRelease(
    /** 版本串，与 release 的 tag 一致（如 v1.0.1） */
    val version: String,
    val htmlUrl: String,
    /** 安装包直链：这个 release 没传 apk 时为 null */
    val apkUrl: String? = null
) {
    /**
     * 「下载」到底跳哪：有直链就走直链（少一层「自己在发布页里找附件」），没有就退回发布页。
     *
     * 这个选择只能在这里做：release 有没有传 apk 是运行时才知道的，写死任何一个地址都会
     * 在另一种情形下错（拼不存在的直链 → 404，只给发布页 → 多绕一步）。
     */
    val downloadUrl: String get() = apkUrl ?: htmlUrl
}

/**
 * 检查更新的结果。三种处境要分开：[NoRelease]（仓库还没发过任何版本）不能被当成失败报给用户——
 * 它其实是「没有可更新的版本」，与 [Failed] 的「这次没查成」不是一回事。
 */
sealed interface UpdateCheckResult {
    data class Latest(val release: LatestRelease) : UpdateCheckResult
    data object NoRelease : UpdateCheckResult
    data object Failed : UpdateCheckResult
}

/**
 * 查所选平台的的最新 release（两个平台的公开接口都不需要认证）。
 * [repositoryUrl] 由调用方从 strings.xml 的仓库地址资源传进来——每个源各一条，
 * 接口与发布页都从它推出来（见 [latestReleaseApiUrl]）。
 * 用 HttpURLConnection 而不是 OkHttp：本应用只有这一处 GET，为它引一个 HTTP 客户端不划算
 * （识别请求走的也是同一个原生接口，见 AiVisionClient）。
 * 差别要记住：**GitHub 的接口要求带 User-Agent**，缺了直接 403；OkHttp 自带，HttpURLConnection 不自带。
 * Gitee 不要求这个头，带了也无害。
 */
class UpdateChecker(
    private val source: UpdateSource,
    private val repositoryUrl: String
) {

    private val latestReleaseUrl: String get() = latestReleaseApiUrl(source, repositoryUrl)

    suspend fun checkLatest(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val response = runCatching {
            httpRequest(
                url = latestReleaseUrl,
                method = "GET",
                connectTimeoutMs = CONNECT_TIMEOUT_MS,
                readTimeoutMs = READ_TIMEOUT_MS,
                headers = mapOf(
                    // GitHub 的接口用它选版本，Gitee 不认这个头也无害（实测两者都回 200）
                    "Accept" to "application/vnd.github+json",
                    // GitHub 的接口要求带 User-Agent，缺了直接 403；HttpURLConnection 不自带
                    "User-Agent" to "FreshNow"
                )
            )
        }.getOrElse { e ->
            // 断网、超时、地址写错都归到「失败」：调用方对这三者的反应是同一个——说一句没查成就完，
            // 检查更新失败不该打断用户手上的事，所以不往上抛
            Log.w(TAG, "检查更新失败：${e.message}")
            return@withContext UpdateCheckResult.Failed
        }

        when (response.code) {
            // 仓库一个 release 都没发过时接口就是 404（两个平台都是这个语义）
            HttpURLConnection.HTTP_NOT_FOUND -> UpdateCheckResult.NoRelease
            in 200..299 -> parseLatestRelease(response.body, releasesPageUrl(repositoryUrl))
                ?.let { UpdateCheckResult.Latest(it) }
                ?: UpdateCheckResult.Failed
            else -> {
                Log.w(TAG, "检查更新失败：HTTP ${response.code}")
                UpdateCheckResult.Failed
            }
        }
    }

    private companion object {
        const val TAG = "UpdateChecker"
    }
}

/**
 * 从 release 接口的返回里取出版本、发布页与安装包直链。取不出必需字段（tag 为空、JSON 不合法）
 * 返回 null，由调用方按失败处理。
 *
 * [fallbackUrl] 是接口没给 html_url 时的退路（仓库的发布页，见 [releasesPageUrl]）——
 * 少了它，「下载」按钮会因为少一个字段变成按下去没反应的按钮。
 *
 * 提为 internal 是为了能直接拿一段 JSON 单测：网络那一层在真机上打桩不值当，而这段解析才是
 * 「拿到的版本号对不对」的判据。
 */
internal fun parseLatestRelease(body: String, fallbackUrl: String): LatestRelease? {
    val json = runCatching { appJson.parseToJsonElement(body).jsonObject }.getOrNull()
        ?: return null
    val version = json["tag_name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        ?: return null
    val htmlUrl = json["html_url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        ?: fallbackUrl
    return LatestRelease(version = version, htmlUrl = htmlUrl, apkUrl = apkAssetUrl(json))
}

/**
 * 没传 apk 时返回 null（由 [LatestRelease.downloadUrl] 退回发布页）。
 * 按后缀认而不是按名字认 "app-release.apk"：产物改名不该让「下载」退回发布页，而名字对上的其他文件也不一定是安装包。
 * 资产字段缺失或不是数组时同样给 null——接口多一个字段不该让整条检查更新变成「没查成」。
 */
private fun apkAssetUrl(json: JsonObject): String? = runCatching {
    json["assets"]?.jsonArray?.firstNotNullOfOrNull { asset ->
        val assetJson = asset.jsonObject
        val name = assetJson["name"]?.jsonPrimitive?.contentOrNull ?: return@firstNotNullOfOrNull null
        if (!name.endsWith(".apk", ignoreCase = true)) return@firstNotNullOfOrNull null
        assetJson["browser_download_url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
    }
}.getOrNull()

/**
 * 比较两个版本串（只认 x.y.z 三段数字，允许带 v 前缀）。返回 >0 表示 [a] 新于 [b]，=0 相同。
 *
 * 多出来的段按 0 补（1.2 与 1.2.0 视为相同），认不出的段也按 0 算——版本号是仓库里手填的，
 * 出现 1.0.0-beta 这类写法时宁可当作「不比它新」，也不要让比较抛异常把检查更新弄崩。
 */
fun compareVersions(a: String, b: String): Int {
    fun parts(version: String): List<Int> = version.trim()
        .removePrefix("v")
        .removePrefix("V")
        .split('.')
        .map { it.toIntOrNull() ?: 0 }

    val left = parts(a)
    val right = parts(b)
    for (i in 0 until maxOf(left.size, right.size)) {
        val x = left.getOrElse(i) { 0 }
        val y = right.getOrElse(i) { 0 }
        if (x != y) return x - y
    }
    return 0
}

/**
 * 该不该向用户提示这个版本。两个条件缺一不可：**确实更新**（不比当前旧，也不能相同），且**没被忽略过**。
 * [ignoredVersion] 是用户点过「关闭」的那个版本号，比的是一个具体的版本而不是一个开关：
 * 忽略过 1.0.1 之后发了 1.0.2，仍然要提示。
 * 抽成纯函数是为了能直接断言四种组合：它是「冷启动该不该弹窗」的全部判据，
 * 而弹窗本身要靠真有一个 release 才能端到端验（见 AutoUpdateCheck）。
 */
internal fun shouldPromptUpdate(
    release: LatestRelease,
    currentVersion: String,
    ignoredVersion: String?
): Boolean = compareVersions(release.version, currentVersion) > 0 && release.version != ignoredVersion
