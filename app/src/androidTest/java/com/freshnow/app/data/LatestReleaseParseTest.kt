package com.freshnow.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 放在仪器化测试里是因为 org.json 是平台类，JVM 单测里只有桩（同 AiVisionClientTest）。
 *
 * 取不出版本号就等于没查成：宁可当失败说一句「检查更新失败」，也不能拿一个空版本号去比大小
 * ——那会静默地得出「已是最新」，而用户以为自己查过了。
 */
@RunWith(AndroidJUnit4::class)
class LatestReleaseParseTest {

    @Test
    fun takesTagAndPage() {
        val body = """
            {
              "tag_name": "v1.0.1",
              "html_url": "https://github.com/Polyaris-0413/FreshNow/releases/tag/v1.0.1",
              "name": "1.0.1"
            }
        """.trimIndent()

        val release = requireNotNull(parseLatestRelease(body, FALLBACK_URL))

        assertEquals("v1.0.1", release.version)
        assertEquals("https://github.com/Polyaris-0413/FreshNow/releases/tag/v1.0.1", release.htmlUrl)
    }

    /** 对话框的「下载」不能因为少个字段就变成一个没反应的按钮 */
    @Test
    fun withoutHtmlUrl_fallsBackToReleasesPage() {
        val release = requireNotNull(parseLatestRelease("""{"tag_name":"v1.0.1"}""", FALLBACK_URL))

        assertEquals(FALLBACK_URL, release.htmlUrl)
    }

    /**
     * 直链省掉「自己在发布页里找 release、再找附件」那一步。
     *
     * 资产里的非 apk（校验和、源码包）不能被当成安装包：点下去下回来一个 txt，用户拿到手里没法装。
     */
    @Test
    fun takesApkAssetAsDownloadUrl() {
        val body = """
            {
              "tag_name": "v1.7.0",
              "html_url": "https://github.com/Polyaris-0413/FreshNow/releases/tag/v1.7.0",
              "assets": [
                { "name": "checksums.txt", "browser_download_url": "https://github.com/Polyaris-0413/FreshNow/releases/download/v1.7.0/checksums.txt" },
                { "name": "app-release.apk", "browser_download_url": "https://github.com/Polyaris-0413/FreshNow/releases/download/v1.7.0/app-release.apk" }
              ]
            }
        """.trimIndent()

        val release = requireNotNull(parseLatestRelease(body, FALLBACK_URL))

        assertEquals(
            "https://github.com/Polyaris-0413/FreshNow/releases/download/v1.7.0/app-release.apk",
            release.downloadUrl
        )
        // 发布页仍留着：它是这个 release 的出处，不是下载地址的退路而已
        assertEquals("https://github.com/Polyaris-0413/FreshNow/releases/tag/v1.7.0", release.htmlUrl)
    }

    /**
     * 发布页至少能看能点，而拼一个不存在的直链会把用户送到 404。
     */
    @Test
    fun withoutApkAsset_downloadFallsBackToPage() {
        val onlyOtherAssets = """
            {
              "tag_name": "v1.7.0",
              "html_url": "https://github.com/Polyaris-0413/FreshNow/releases/tag/v1.7.0",
              "assets": [
                { "name": "checksums.txt", "browser_download_url": "https://github.com/Polyaris-0413/FreshNow/releases/download/v1.7.0/checksums.txt" }
              ]
            }
        """.trimIndent()

        assertEquals(
            "https://github.com/Polyaris-0413/FreshNow/releases/tag/v1.7.0",
            requireNotNull(parseLatestRelease(onlyOtherAssets, FALLBACK_URL)).downloadUrl
        )

        // 资产字段缺失、或不是数组：不能因此整条解析失败，也不能崩
        val noAssets = requireNotNull(
            parseLatestRelease("""{"tag_name":"v1.7.0","html_url":"$FALLBACK_URL"}""", FALLBACK_URL)
        )
        assertEquals(FALLBACK_URL, noAssets.downloadUrl)

        val assetsNotArray = requireNotNull(
            parseLatestRelease("""{"tag_name":"v1.7.0","html_url":"$FALLBACK_URL","assets":"none"}""", FALLBACK_URL)
        )
        assertEquals(FALLBACK_URL, assetsNotArray.downloadUrl)
    }

    @Test
    fun invalidInput_returnsNull() {
        assertNull(parseLatestRelease("""{"tag_name":""}""", FALLBACK_URL))
        assertNull(parseLatestRelease("""{"name":"1.0.0"}""", FALLBACK_URL))
        assertNull(parseLatestRelease("not json", FALLBACK_URL))
        assertNull(parseLatestRelease("", FALLBACK_URL))
    }

    /**
     * 版本文件是 Gitee 那条路的数据源（见 UpdateChannel.VersionFile）。
     * 它没有发布页字段，页面地址按 tag 拼——发布页那个路由两个平台都有。
     */
    @Test
    fun versionFile_takesVersionAndApk() {
        val body = """
            {
              "version": "v1.8.1",
              "apk": "https://gitee.com/Polyaris/FreshNow/releases/download/v1.8.1/app-release.apk"
            }
        """.trimIndent()

        val release = requireNotNull(parseVersionFile(body, GITEE_REPO))

        assertEquals("v1.8.1", release.version)
        assertEquals("$GITEE_REPO/releases/tag/v1.8.1", release.htmlUrl)
        assertEquals(
            "https://gitee.com/Polyaris/FreshNow/releases/download/v1.8.1/app-release.apk",
            release.downloadUrl
        )
    }

    /** 文件里少 apk 时退回发布页：「下载」不能因为少个字段就按下去没反应 */
    @Test
    fun versionFileWithoutApk_fallsBackToTagPage() {
        val release = requireNotNull(parseVersionFile("""{"version":"v1.8.1"}""", GITEE_REPO))

        assertEquals("$GITEE_REPO/releases/tag/v1.8.1", release.downloadUrl)
    }

    /**
     * 额外字段不该让整条检查更新变成「没查成」；而版本号缺失就等于没查成——
     * 拿空版本号去比大小会静默得出「已是最新」。
     */
    @Test
    fun versionFile_invalidInput_returnsNull() {
        val withExtra = requireNotNull(
            parseVersionFile("""{"version":"v1.8.1","notes":"x","files":[1,2]}""", GITEE_REPO)
        )
        assertEquals("v1.8.1", withExtra.version)

        assertNull(parseVersionFile("""{"apk":"https://example.invalid/a.apk"}""", GITEE_REPO))
        assertNull(parseVersionFile("""{"version":""}""", GITEE_REPO))
        assertNull(parseVersionFile("not json", GITEE_REPO))
        assertNull(parseVersionFile("", GITEE_REPO))
    }

    private companion object {
        /** 仓库的发布页，由调用方从仓库地址推出来（见 data/UpdateChecker.kt 的 releasesPageUrl） */
        const val FALLBACK_URL = "https://github.com/Polyaris-0413/FreshNow/releases/latest"

        /** Gitee 仓库页，版本文件的页面地址从它推出来 */
        const val GITEE_REPO = "https://gitee.com/Polyaris/FreshNow"
    }
}
