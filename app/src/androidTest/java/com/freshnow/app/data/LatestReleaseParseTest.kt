package com.freshnow.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

        val release = requireNotNull(parseLatestRelease(body))

        assertEquals("v1.0.1", release.version)
        assertEquals("https://github.com/Polyaris-0413/FreshNow/releases/tag/v1.0.1", release.htmlUrl)
    }

    /** 接口没给 html_url 时退回发布页：对话框的「下载」不能因为少个字段就变成一个没反应的按钮 */
    @Test
    fun withoutHtmlUrl_fallsBackToReleasesPage() {
        val release = requireNotNull(parseLatestRelease("""{"tag_name":"v1.0.1"}"""))

        assertTrue(release.htmlUrl.endsWith("/releases/latest"))
    }

    @Test
    fun invalidInput_returnsNull() {
        assertNull(parseLatestRelease("""{"tag_name":""}"""))
        assertNull(parseLatestRelease("""{"name":"1.0.0"}"""))
        assertNull(parseLatestRelease("not json"))
        assertNull(parseLatestRelease(""))
    }
}
