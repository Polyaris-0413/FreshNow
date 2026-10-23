package com.freshnow.app.ui.about

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 开源声明页的数据来自构建期生成的 `artifacts.json`（见 app/build.gradle.kts 的 licensee），
 * 这里只验「把依赖图摊成一行行给人看」这一步：一个依赖一行，不归并、不遗漏。
 */
class OpenSourceProjectsTest {

    @Test
    fun `每个依赖各占一行且顺序不变`() {
        val projects = parseProjects(
            """
            [
              {"groupId":"androidx.activity","artifactId":"activity","name":"Activity",
               "spdxLicenses":[{"identifier":"Apache-2.0","name":"Apache License 2.0","url":"https://a"}],
               "scm":{"url":"https://cs.android.com/androidx"}},
              {"groupId":"androidx.activity","artifactId":"activity-compose","name":"Activity Compose",
               "spdxLicenses":[{"identifier":"Apache-2.0","name":"Apache License 2.0","url":"https://a"}],
               "scm":{"url":"https://cs.android.com/androidx"}}
            ]
            """.trimIndent()
        )

        assertEquals(listOf("Activity", "Activity Compose"), projects.map { it.name })
    }

    /** 一个依赖可能同时列了多个协议（CameraX 里就有 Apache + BSD 的），都保留 */
    @Test
    fun `一个依赖的多个协议合在一行显示`() {
        val projects = parseProjects(
            """
            [
              {"groupId":"androidx.camera","artifactId":"camera-core","name":"Camera Core",
               "spdxLicenses":[
                 {"identifier":"Apache-2.0","name":"Apache License 2.0","url":"https://a"},
                 {"identifier":"BSD-3-Clause","name":"BSD 3-Clause License","url":"https://b"}
               ],
               "scm":{"url":"https://cs.android.com/androidx"}}
            ]
            """.trimIndent()
        )

        assertEquals("Apache License 2.0 / BSD 3-Clause License", projects.single().license)
    }

    @Test
    fun `没有名称的依赖用坐标顶替`() {
        val projects = parseProjects(
            """
            [
              {"groupId":"com.example","artifactId":"widget",
               "spdxLicenses":[{"identifier":"Apache-2.0","name":"Apache License 2.0","url":"https://a"}]}
            ]
            """.trimIndent()
        )

        assertEquals("com.example:widget", projects.single().name)
    }

    @Test
    fun `认不出 SPDX 的协议也显示`() {
        val projects = parseProjects(
            """
            [
              {"groupId":"org.slf4j","artifactId":"slf4j-api","name":"SLF4J API Module",
               "unknownLicenses":[{"name":"MIT","url":"https://opensource.org/license/mit"}],
               "scm":{"url":"https://github.com/qos-ch/slf4j.git"}}
            ]
            """.trimIndent()
        )

        assertEquals("MIT", projects.single().license)
        assertEquals("https://github.com/qos-ch/slf4j", projects.single().url)
    }

    /** 没有仓库地址的依赖不该变成可点：点开一个空链接比不可点更糟 */
    @Test
    fun `没有仓库地址时给空串`() {
        val projects = parseProjects(
            """
            [
              {"groupId":"com.example","artifactId":"widget","name":"Widget",
               "spdxLicenses":[{"identifier":"Apache-2.0","name":"Apache License 2.0","url":"https://a"}]}
            ]
            """.trimIndent()
        )

        assertEquals("", projects.single().url)
    }

    /** 坏数据不该把整页搞崩：读不出来就当没有 */
    @Test
    fun `解析不了的内容返回空列表`() {
        assertEquals(emptyList<Any>(), parseProjects("这不是 JSON"))
    }
}
