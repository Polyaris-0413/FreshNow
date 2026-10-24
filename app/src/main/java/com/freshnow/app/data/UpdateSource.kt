package com.freshnow.app.data

/**
 * 检查更新去哪个平台查。
 *
 * 两个平台都托管着同一个应用的发行版，但仓库路径不同（GitHub 是 Polyaris-0413/FreshNow，
 * Gitee 是 Polyaris/FreshNow）—— GitHub 与 Gitee 的用户名本来就不是同一个，
 * 因此仓库地址各有一份资源（strings.xml），推不出来。
 *
 * 用户手动选而不是自动回落：大陆网络下 GitHub 多半是等满超时，自动回落会让每次检查都先卡十秒。
 */
enum class UpdateSource(
    /** 仓库网页地址里的主机名（不带末尾斜杠），用来从整条地址里取出 owner/name */
    internal val host: String
) {
    GITHUB("github.com"),
    GITEE("gitee.com");

    /** 每个源走哪条通道见 [channel]：枚举只表达「用户选了谁」，怎么取版本是数据层的事 */
    internal fun channel(): UpdateChannel = when (this) {
        GITHUB -> UpdateChannel.ReleaseApi(GITHUB_API_PREFIX)
        GITEE -> UpdateChannel.VersionFile(VERSION_FILE_BRANCH)
    }
}

/**
 * 检查更新走哪条通道。
 *
 * GitHub 用发布接口：它的匿名配额是每小时 60 次、按 IP，一天一次冷启动绰绰有余。
 *
 * Gitee 不能用它的发布接口：匿名访问按 IP 限流，超过直接回 403 Rate Limit Exceeded，
 * 且实测十多分钟不恢复（共享出口 IP 上更容易被打满，比如校园网、公司网）。
 * 而 raw 与 releases/download 这两条路不走那套配额，所以 Gitee 改读仓库里的版本文件。
 */
internal sealed interface UpdateChannel {
    /** 平台的发布接口，[apiPrefix] 后面接 owner/name/releases/latest */
    data class ReleaseApi(val apiPrefix: String) : UpdateChannel

    /** 仓库里的版本文件（raw 通道），[branch] 是它所在的分支 */
    data class VersionFile(val branch: String) : UpdateChannel
}

private const val GITHUB_API_PREFIX = "https://api.github.com/repos/"

/** 版本文件所在分支：仓库默认分支，改了分支名这里跟着改 */
private const val VERSION_FILE_BRANCH = "main"

/** 版本文件名，由发版流程写入（见 .github/workflows/sync-gitee-release.yml） */
internal const val VERSION_FILE_NAME = "version.json"
