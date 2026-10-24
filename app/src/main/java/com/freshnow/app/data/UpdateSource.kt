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
    /** 发布接口前缀，后面接 owner/name */
    internal val apiPrefix: String,
    /** 仓库网页地址的主机名（不带末尾斜杠），用来从整条地址里取出 owner/name */
    internal val host: String
) {
    GITHUB("https://api.github.com/repos/", "github.com"),
    GITEE("https://gitee.com/api/v5/repos/", "gitee.com")
}
