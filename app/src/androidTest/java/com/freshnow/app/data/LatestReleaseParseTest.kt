package com.freshnow.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * release 返回的解析。放在仪器化测试里是因为 org.json 是平台类，JVM 单测里只有桩（同 AiVisionClientTest）。
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

    /** 接口没给 html_url 时退回发布页：对话框的「下载」不能因为少个字段就变成一个没反应的按钮 */
    @Test
    fun withoutHtmlUrl_fallsBackToReleasesPage() {
        val release = requireNotNull(parseLatestRelease("""{"tag_name":"v1.0.1"}""", FALLBACK_URL))

        assertEquals(FALLBACK_URL, release.htmlUrl)
    }

    /**
     * 有安装包资产时「下载」走直链：发布页还要用户自己找 release、再找附件，直链一点就下。
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
     * 这个 release 没传 apk（或根本没传资产）时退回发布页：发布页至少能看能点，
     * 而拼一个不存在的直链会把用户送到 404。
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

    private companion object {
        /** 仓库的发布页，由调用方从仓库地址推出来（见 data/UpdateChecker.kt 的 releasesPageUrl） */
        const val FALLBACK_URL = "https://github.com/Polyaris-0413/FreshNow/releases/latest"
    }
}
