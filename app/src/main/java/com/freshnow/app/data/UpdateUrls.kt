package com.freshnow.app.data

/**
 * 更新源相关的地址推导。
 *
 * 与 [UpdateChecker] 分开放：这些是纯字符串拼接（能单测），而那个类管网络与状态码。
 */

/**
 * 从仓库页地址（strings.xml 的 project_repository_url / project_repository_url_gitee）取出仓库标识 owner/name。
 *
 * 不把仓库标识另存一份：那样「访问仓库」与「检查更新」就有两个来源，改仓库时漏一处，
 * 而漏掉的表现是「界面上的仓库是对的、检查更新却查不到东西」。主机名从 [source] 取，
 * 两个平台的仓库路径写法相同但域名不同，一个写死的域名会把另一个源的地址切出空 slug。
 * 不是该源地址时返回空串：那样请求会 404，由调用方按「这次没查成」处理，
 * 而不会把整条地址拼进请求路径里当成 owner/name。
 */
internal fun repositorySlug(source: UpdateSource, repositoryUrl: String): String =
    trimmedRepositoryUrl(repositoryUrl).substringAfter("${source.host}/", missingDelimiterValue = "")

/** 发布接口地址，只有走 [UpdateChannel.ReleaseApi] 的源有 */
internal fun latestReleaseApiUrl(source: UpdateSource, repositoryUrl: String): String {
    val apiPrefix = (source.channel() as? UpdateChannel.ReleaseApi)?.apiPrefix
        ?: error("$source 走的是版本文件通道，没有发布接口地址")
    return "$apiPrefix${repositorySlug(source, repositoryUrl)}/releases/latest"
}

/** 版本文件地址（raw 通道）：不发配额，也没有接口那一层 JSON 包装 */
internal fun versionFileUrl(source: UpdateSource, repositoryUrl: String): String {
    val branch = (source.channel() as? UpdateChannel.VersionFile)?.branch
        ?: error("$source 走的是发布接口通道，没有版本文件")
    return "${trimmedRepositoryUrl(repositoryUrl)}/raw/$branch/$VERSION_FILE_NAME"
}

/** 接口没给 html_url 时退回这里，两条路都是同一页（两个平台的 /releases/latest 都可用） */
internal fun releasesPageUrl(repositoryUrl: String): String =
    "${trimmedRepositoryUrl(repositoryUrl)}/releases/latest"

/** 某个版本自己的发布页。版本文件里没有发布页字段，只能按 tag 拼，两个平台都是这个路由 */
internal fun releaseTagUrl(repositoryUrl: String, version: String): String =
    "${trimmedRepositoryUrl(repositoryUrl)}/releases/tag/$version"

/** 去掉首尾空白、末尾斜杠与 .git 后缀，网页地址与 clone 地址因此推出同一结果 */
private fun trimmedRepositoryUrl(repositoryUrl: String): String =
    repositoryUrl.trim().trimEnd('/').removeSuffix(".git")
