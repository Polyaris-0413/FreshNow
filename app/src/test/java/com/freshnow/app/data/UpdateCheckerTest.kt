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

    /** 确实更新且没被忽略过 → 提示 */
    @Test
    fun promptsForNewerVersion() {
        assertTrue(shouldPromptUpdate(release("v1.0.1"), currentVersion = "1.0.0", ignoredVersion = null))
    }

    /** 点过「关闭」的那个版本不再提示 */
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
        val url = repositoryUrlFromStringsXml()
        val slug = repositorySlug(url)

        assertEquals("资源里的仓库地址应是 https://github.com/<owner>/<name>：$url", 2, slug.split('/').size)
        assertEquals("https://api.github.com/repos/$slug/releases/latest", latestReleaseApiUrl(url))
        assertEquals("${url.trimEnd('/')}/releases/latest", releasesPageUrl(url))
    }

    /** 地址末尾多一个斜杠、或写成 clone 的 .git 形式，推出来的要是同一对 URL */
    @Test
    fun repositoryUrl_toleratesTrailingSlashAndGitSuffix() {
        val expected = "Polyaris-0413/FreshNow"

        assertEquals(expected, repositorySlug("https://github.com/Polyaris-0413/FreshNow/"))
        assertEquals(expected, repositorySlug("https://github.com/Polyaris-0413/FreshNow.git"))
        assertEquals(
            "https://api.github.com/repos/$expected/releases/latest",
            latestReleaseApiUrl("https://github.com/Polyaris-0413/FreshNow.git")
        )
    }

    /**
     * 取资源里的仓库地址。
     *
     * 与 ThemeWindowBackgroundTest 同一个办法：那类「两处必须一致」的取值只有读真文件才验得到，
     * 而这里是同一类约束——仓库地址从资源推到接口地址。
     */
    private fun repositoryUrlFromStringsXml(): String {
        val relativePath = "src/main/res/values/strings.xml"
        val file = listOf(File(relativePath), File("app/$relativePath"))
            .firstOrNull { it.isFile }
            ?: error("找不到 $relativePath（当前工作目录 ${File("").absolutePath}）")

        return REPOSITORY_URL.find(file.readText())?.groupValues?.get(1)
            ?: error("$relativePath 里没有 project_repository_url")
    }

    private fun release(version: String) =
        LatestRelease(version = version, htmlUrl = "https://example.invalid/releases/latest")

    private companion object {
        val REPOSITORY_URL =
            Regex("""<string name="project_repository_url"[^>]*>([^<]+)</string>""")
    }
}
