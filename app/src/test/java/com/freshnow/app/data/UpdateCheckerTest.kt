package com.freshnow.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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

    private fun release(version: String) =
        LatestRelease(version = version, htmlUrl = "https://example.invalid/releases/latest")
}
