package com.freshnow.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 版本比较。这是「该不该弹更新提示」的判据（JSON 解析那半在 androidTest 里，见 LatestReleaseParseTest）
 * ——它错了的表现是「明明有新版却不提示」或「同一版本反复提示」，所以逐种写法钉住。
 */
class UpdateCheckerTest {

    @Test
    fun compare_newerPatchWins() {
        assertTrue(compareVersions("1.0.1", "1.0.0") > 0)
        assertTrue(compareVersions("1.1.0", "1.0.9") > 0)
        assertTrue(compareVersions("2.0.0", "1.9.9") > 0)
    }

    @Test
    fun compare_sameOrOlderIsNotNewer() {
        assertEquals(0, compareVersions("1.0.0", "1.0.0"))
        assertTrue(compareVersions("1.0.0", "1.0.1") < 0)
        assertTrue(compareVersions("0.1.0", "1.0.0") < 0)
    }

    /** release 的 tag 习惯带 v，包里写的是裸版本号：两边都要认 */
    @Test
    fun compare_ignoresVPrefixAndSpaces() {
        assertEquals(0, compareVersions("v1.0.0", "1.0.0"))
        assertEquals(0, compareVersions(" V1.0.0 ", "1.0.0"))
        assertTrue(compareVersions("v1.2.0", "1.1.0") > 0)
    }

    /** 段数不齐按时补 0：1.2 与 1.2.0 是同一个版本 */
    @Test
    fun compare_missingSegmentsCountAsZero() {
        assertEquals(0, compareVersions("1.2", "1.2.0"))
        assertTrue(compareVersions("1.2.1", "1.2") > 0)
    }

    /** 认不出的段按 0：宁可当作「不比它新」，也不能让比较抛异常把检查更新弄崩 */
    @Test
    fun compare_unrecognizableSegmentsFallBackToZero() {
        assertEquals(0, compareVersions("1.0.0-beta", "1.0.0"))
        assertEquals(0, compareVersions("abc", "0"))
        assertEquals(0, compareVersions("", "0"))
    }

    @Test
    fun promptsForNewerVersion() {
        assertTrue(shouldPromptUpdate(release("v1.0.1"), currentVersion = "1.0.0", ignoredVersion = null))
    }

    @Test
    fun doesNotPromptForIgnoredVersion() {
        assertTrue(
            !shouldPromptUpdate(release("v1.0.1"), currentVersion = "1.0.0", ignoredVersion = "v1.0.1")
        )
    }

    /** 忽略过 1.0.1 之后发了 1.0.2：仍要提示（忽略的是版本，不是一个总开关） */
    @Test
    fun promptsAgainForTheNextVersion() {
        assertTrue(shouldPromptUpdate(release("v1.0.2"), currentVersion = "1.0.0", ignoredVersion = "v1.0.1"))
    }

    /** 同一个版本、比当前旧的版本都不提示：重复报同一件事比不报还烦 */
    @Test
    fun doesNotPromptForSameOrOlderVersion() {
        assertTrue(!shouldPromptUpdate(release("v1.0.0"), currentVersion = "1.0.0", ignoredVersion = null))
        assertTrue(!shouldPromptUpdate(release("v0.9.9"), currentVersion = "1.0.0", ignoredVersion = null))
    }

    /** 当前版本没带 v、tag 带了 v（发布时就是这样）也得比得出来 */
    @Test
    fun matchesVersionsAcrossVPrefix() {
        assertTrue(!shouldPromptUpdate(release("v1.0.0"), currentVersion = "v1.0.0", ignoredVersion = null))
        assertTrue(shouldPromptUpdate(release("V1.0.1"), currentVersion = "1.0.0", ignoredVersion = null))
    }

    /**
     * 仓库地址只有 strings.xml 的 project_repository_url 一处来源：接口与发布页都从它推出来。
     *
     * 这里读的是真正的资源：写成别的主机名、或路径少一段时本测试先失败，
     * 而不是等到检查更新静默地查不到东西。
     */
    @Test
    fun repositoryResource_drivesApiAndReleasesPage() {
        val url = repositoryUrlFromStringsXml("project_repository_url")
        val slug = repositorySlug(UpdateSource.GITHUB, url)

        assertEquals("资源里的仓库地址应是 https://github.com/<owner>/<name>：$url", 2, slug.split('/').size)
        assertEquals(
            "https://api.github.com/repos/$slug/releases/latest",
            latestReleaseApiUrl(UpdateSource.GITHUB, url)
        )
        assertEquals("${url.trimEnd('/')}/releases/latest", releasesPageUrl(url))
    }

    /**
     * Gitee 的仓库路径与 GitHub 不是同一个（用户名不同），地址也各有一份资源。
     * 这里验的是：主机名取自源而不是写死的 github.com；而且 Gitee 走的是版本文件而不是发布接口。
     */
    @Test
    fun giteeRepositoryResource_drivesVersionFileAndPages() {
        val url = repositoryUrlFromStringsXml("project_repository_url_gitee")
        val slug = repositorySlug(UpdateSource.GITEE, url)

        assertEquals("资源里的仓库地址应是 https://gitee.com/<owner>/<name>：$url", 2, slug.split('/').size)
        assertEquals("${url.trimEnd('/')}/raw/main/version.json", versionFileUrl(UpdateSource.GITEE, url))
        assertEquals("${url.trimEnd('/')}/releases/latest", releasesPageUrl(url))
        assertEquals("${url.trimEnd('/')}/releases/tag/v1.8.1", releaseTagUrl(url, "v1.8.1"))
    }

    /** 两个通道各走各的：给错源时直接报出来，而不是拼出一个必然 404 的地址 */
    @Test(expected = IllegalStateException::class)
    fun giteeHasNoReleaseApi() {
        latestReleaseApiUrl(UpdateSource.GITEE, "https://gitee.com/Polyaris/FreshNow")
    }

    @Test(expected = IllegalStateException::class)
    fun githubHasNoVersionFile() {
        versionFileUrl(UpdateSource.GITHUB, "https://github.com/Polyaris-0413/FreshNow")
    }

    /** 两个源的仓库标识各算各的：拿 GitHub 地址去问 Gitee 会得到空 slug，而不是把整条地址当路径 */
    @Test
    fun slugIsDerivedFromTheChosenSourceOnly() {
        assertEquals(
            "Polyaris/FreshNow",
            repositorySlug(UpdateSource.GITEE, "https://gitee.com/Polyaris/FreshNow")
        )
        assertEquals(
            "",
            repositorySlug(UpdateSource.GITEE, "https://github.com/Polyaris-0413/FreshNow")
        )
    }

    @Test
    fun repositoryUrl_toleratesTrailingSlashAndGitSuffix() {
        val expected = "Polyaris-0413/FreshNow"

        assertEquals(expected, repositorySlug(UpdateSource.GITHUB, "https://github.com/Polyaris-0413/FreshNow/"))
        assertEquals(expected, repositorySlug(UpdateSource.GITHUB, "https://github.com/Polyaris-0413/FreshNow.git"))
        assertEquals(
            "https://api.github.com/repos/$expected/releases/latest",
            latestReleaseApiUrl(UpdateSource.GITHUB, "https://github.com/Polyaris-0413/FreshNow.git")
        )
    }

    /**
     * 取资源里的仓库地址。
     *
     * 与 ThemeWindowBackgroundTest 同一个办法：那类「两处必须一致」的取值只有读真文件才验得到，
     * 而这里是同一类约束——仓库地址从资源推到接口地址。
     */
    private fun repositoryUrlFromStringsXml(name: String): String {
        val relativePath = "src/main/res/values/strings.xml"
        val file = listOf(File(relativePath), File("app/$relativePath"))
            .firstOrNull { it.isFile }
            ?: error("找不到 $relativePath（当前工作目录 ${File("").absolutePath}）")

        return Regex("""<string name="$name"[^>]*>([^<]+)</string>""")
            .find(file.readText())?.groupValues?.get(1)
            ?: error("$relativePath 里没有 $name")
    }

    private fun release(version: String) =
        LatestRelease(version = version, htmlUrl = "https://example.invalid/releases/latest")
}
