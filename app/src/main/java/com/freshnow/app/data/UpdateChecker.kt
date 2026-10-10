package com.freshnow.app.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 接口地址前缀。仓库标识拼在它后面
 */
private const val API_PREFIX = "https://api.github.com/repos/"

/**
 * 从仓库页地址（strings.xml 的 project_repository_url）取出仓库标识 owner/name。
 *
 * 不把仓库标识另存一份：那样「访问仓库」与「检查更新」就有两个来源，改仓库时漏一处，
 * 而漏掉的表现是「界面上的仓库是对的、检查更新却查不到东西」。不是 GitHub 地址时原样返回，
 * 接口会 404，由调用方按「这次没查成」处理。
 */
internal fun repositorySlug(repositoryUrl: String): String =
    trimmedRepositoryUrl(repositoryUrl).substringAfter("github.com/")

/**
 * 「最新 release」接口地址，由仓库页地址推出来
 */
internal fun latestReleaseApiUrl(repositoryUrl: String): String =
    "$API_PREFIX${repositorySlug(repositoryUrl)}/releases/latest"

/** 发布页。接口没给 html_url 时退回这里，两条路都是同一页 */
internal fun releasesPageUrl(repositoryUrl: String): String =
    "${trimmedRepositoryUrl(repositoryUrl)}/releases/latest"

/** 去掉首尾空白、末尾斜杠与 .git 后缀，网页地址与 clone 地址因此推出同一结果 */
private fun trimmedRepositoryUrl(repositoryUrl: String): String =
    repositoryUrl.trim().trimEnd('/').removeSuffix(".git")

private const val CONNECT_TIMEOUT_MS = 10_000
private const val READ_TIMEOUT_MS = 10_000

/** GitHub 上的最新 release */
data class LatestRelease(
    /** 版本串，与 release 的 tag 一致（如 v1.0.1） */
    val version: String,
    /** 发布页地址，交给浏览器打开 */
    val htmlUrl: String
)

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
 * 查 GitHub 的最新 release（公开接口，不需要认证）。
 *
 * [repositoryUrl] 是仓库页地址，由调用方从 strings.xml 的 project_repository_url 传进来——
 * 仓库地址因此只有那一条资源可用，接口与发布页都从它推出来（见 [latestReleaseApiUrl]）。
 *
 * 用 HttpURLConnection 而不是 OkHttp：本应用只有这一处 GET，为它引一个 HTTP 客户端不划算
 * （识别请求走的也是同一个原生接口，见 AiVisionClient）。
 *
 * 与用 OkHttp 的一个差别要记住：**GitHub 的接口要求带 User-Agent**，缺了直接 403；
 * OkHttp 自带一个，HttpURLConnection 不自带，所以下面显式设置。
 */
class UpdateChecker(private val repositoryUrl: String) {

    private val latestReleaseUrl: String get() = latestReleaseApiUrl(repositoryUrl)

    suspend fun checkLatest(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val connection = runCatching {
            (URL(latestReleaseUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "FreshNow")
            }
        }.getOrNull() ?: return@withContext UpdateCheckResult.Failed

        try {
            when (val code = connection.responseCode) {
                // 仓库一个 release 都没发过时接口就是 404
                HttpURLConnection.HTTP_NOT_FOUND -> UpdateCheckResult.NoRelease
                in 200..299 -> {
                    val body = connection.inputStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    parseLatestRelease(body, releasesPageUrl(repositoryUrl))
                        ?.let { UpdateCheckResult.Latest(it) }
                        ?: UpdateCheckResult.Failed
                }

                else -> {
                    Log.w(TAG, "检查更新失败：HTTP $code")
                    UpdateCheckResult.Failed
                }
            }
        } catch (e: Exception) {
            // 断网、超时、地址写错都归到「失败」：调用方对这三者的反应是同一个——说一句没查成就完，
            // 检查更新失败不该打断用户手上的事，所以不往上抛
            Log.w(TAG, "检查更新失败：${e.message}")
            UpdateCheckResult.Failed
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TAG = "UpdateChecker"
    }
}

/**
 * 从 release 接口的返回里取出版本与发布页。取不出必需字段（tag 为空、JSON 不合法）返回 null，
 * 由调用方按失败处理。
 *
 * [fallbackUrl] 是接口没给 html_url 时的退路（仓库的发布页，见 [releasesPageUrl]）——
 * 少了它，「下载」按钮会因为少一个字段变成按下去没反应的按钮。
 *
 * 提为 internal 是为了能直接拿一段 JSON 单测：网络那一层在真机上打桩不值当，而这段解析才是
 * 「拿到的版本号对不对」的判据。
 */
internal fun parseLatestRelease(body: String, fallbackUrl: String): LatestRelease? {
    val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
    val version = json.optString("tag_name").takeIf { it.isNotBlank() } ?: return null
    return LatestRelease(
        version = version,
        htmlUrl = json.optString("html_url").ifBlank { fallbackUrl }
    )
}

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
 * 该不该向用户提示这个版本。
 *
 * 两个条件缺一不可：**确实更新**（不比当前旧，也不能相同），且**没被忽略过**。
 *
 * [ignoredVersion] 是用户点过「关闭」的那个版本号，比的是一个具体的版本而不是一个开关：
 * 忽略过 1.0.1 之后发了 1.0.2，仍然要提示。
 *
 * 抽成纯函数是为了能直接断言四种组合（新/旧/同版 × 忽略与否）：它是「冷启动该不该弹窗」的
 * 全部判据，而弹窗本身要靠真有一个 release 才能端到端验（见 AutoUpdateCheck）。
 */
internal fun shouldPromptUpdate(
    release: LatestRelease,
    currentVersion: String,
    ignoredVersion: String?
): Boolean = compareVersions(release.version, currentVersion) > 0 && release.version != ignoredVersion
