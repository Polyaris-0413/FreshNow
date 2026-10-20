package com.freshnow.app.ui.about

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 开源声明页的数据现在来自构建期生成的 `artifacts.json`（见 app/build.gradle.kts 的 licensee），
 * 这里只验「把 217 条依赖归成几条给人看」这一步。
 *
 * 归组的取舍：已知前缀按模块归并（Compose 一组、Ktor 一组……），认不出的 group 原样用 groupId
 * 单独列一条——**宁可难看，不可漏掉**。漏了就是署名缺失，难看只是多一行。
 */
class OpenSourceProjectsTest {

    @Test
    fun `同一个前缀下的依赖归成一条并合并协议`() {
        val projects = parseProjects(
            """
            [
              {"groupId":"androidx.compose.ui","artifactId":"ui","name":"ui",
               "spdxLicenses":[{"identifier":"Apache-2.0","name":"Apache License 2.0","url":"https://a"}],
               "scm":{"url":"https://cs.android.com/androidx"}},
              {"groupId":"androidx.compose.runtime","artifactId":"runtime","name":"runtime",
               "spdxLicenses":[{"identifier":"Apache-2.0","name":"Apache License 2.0","url":"https://a"}],
               "scm":{"url":"https://cs.android.com/androidx"}}
            ]
            """.trimIndent()
        )

        assertEquals(1, projects.size)
        val compose = projects.single()
        assertEquals("Jetpack Compose", compose.name)
        assertEquals("Apache License 2.0", compose.license)
        assertEquals("https://cs.android.com/androidx", compose.url)
    }

    /** SLF4J 这类在 POM 里没写 SPDX id 的走 unknownLicenses，同样要显示出来 */
    @Test
    fun `认不出 SPDX 的协议也显示`() {
        val projects = parseProjects(
            """
            [
              {"groupId":"org.slf4j","artifactId":"slf4j-api","name":"SLF4J API Module",
               "unknownLicenses":[{"name":"MIT","url":"https://opensource.org/license/mit"}],
               "scm":{"url":"https://github.com/qos-ch/slf4j"}}
            ]
            """.trimIndent()
        )

        assertEquals("SLF4J", projects.single().name)
        assertEquals("MIT", projects.single().license)
    }

    /** 没有规则认领的 group 也不能消失：用 groupId 当名字 */
    @Test
    fun `认不出的分组用 groupId 现形`() {
        val projects = parseProjects(
            """
            [
              {"groupId":"com.google.guava","artifactId":"guava","name":"Guava",
               "spdxLicenses":[{"identifier":"Apache-2.0","name":"Apache License 2.0","url":"https://a"}],
               "scm":{"url":"https://github.com/google/guava"}}
            ]
            """.trimIndent()
        )

        assertEquals("com.google.guava", projects.single().name)
        assertEquals("https://github.com/google/guava", projects.single().url)
    }

    /** 同一份 JSON 解析两次给出同样的顺序：列表不能每次进页面都换个排列 */
    @Test
    fun `顺序稳定且已知分组排在前面`() {
        val json = """
            [
              {"groupId":"com.google.guava","artifactId":"guava","name":"Guava",
               "spdxLicenses":[{"identifier":"Apache-2.0","name":"Apache License 2.0","url":"https://a"}]},
              {"groupId":"io.ktor","artifactId":"ktor-client-cio","name":"ktor-client-cio",
               "spdxLicenses":[{"identifier":"Apache-2.0","name":"Apache License 2.0","url":"https://a"}],
               "scm":{"url":"https://github.com/ktorio/ktor.git"}}
            ]
        """.trimIndent()

        val names = parseProjects(json).map { it.name }
        assertEquals(listOf("Ktor", "com.google.guava"), names)
        assertTrue("ktor 的仓库地址不该带 .git", parseProjects(json).first().url.isNotEmpty())
    }

    /** 坏数据不该把整页搞崩：读不出来就当没有 */
    @Test
    fun `解析不了的内容返回空列表`() {
        assertEquals(emptyList<Any>(), parseProjects("这不是 JSON"))
    }
}
