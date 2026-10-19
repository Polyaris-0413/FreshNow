package com.freshnow.app.ui.component

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 底部面板一律走 [FreshNowBottomSheet]：拖动手柄那层方形涟漪在那边统一关掉（原因见它的 KDoc）。
 *
 * 直接调用 ModalBottomSheet 编译得过、跑得通，只是在手柄上多出一块与胶囊不搭的方块涟漪——
 * 这种「看着像小毛病、其实是漏了一层处理」的东西没人会在编译期发现，所以在这里钉住：
 * 新增的面板必须走同一个入口。
 */
class BottomSheetDragHandleRippleTest {

    @Test
    fun modalBottomSheet_isOnlyUsedByTheSharedWrapper() {
        val sourceRoot = appSourceRoot()
        val kotlinSources = sourceRoot
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
        // 路径解析错了会让下面那句「没有违规」白白成立
        assertTrue(
            "没扫到 Kotlin 源码（${sourceRoot.absolutePath}），检查本测试的源码目录解析",
            kotlinSources.size > 10
        )

        val offenders = kotlinSources
            .filter { it.name !in WRAPPERS }
            .filter { callsModalBottomSheet(it) }
            .map { it.relativeTo(sourceRoot).invariantSeparatorsPath }
            .sorted()

        assertTrue(
            "这些文件直接用了 ModalBottomSheet，应改用 FreshNowBottomSheet：$offenders",
            offenders.isEmpty()
        )
    }

    /** 只看代码行：注释里提一嘴 ModalBottomSheet（不带括号或带括号都算）不算违规 */
    private fun callsModalBottomSheet(file: File): Boolean =
        file.readLines().any { line ->
            val code = line.trimStart()
            !code.startsWith("//") && !code.startsWith("*") && MODAL_BOTTOM_SHEET.containsMatchIn(line)
        }

    @Test
    fun sharedWrapper_keepsTheHandleRippleOffAndRestoresContentRipple() {
        val wrapper = appSourceRoot().resolve(WRAPPER_PATH)
        val providedValues = RIPPLE_PROVISION
            .findAll(wrapper.readText())
            .map { it.groupValues[1] }
            .toSet()

        assertTrue(
            "FreshNowBottomSheet 里没有「LocalRippleConfiguration provides null」，手柄的方形涟漪会回来",
            "null" in providedValues
        )
        assertTrue(
            "FreshNowBottomSheet 只把涟漪关掉了、没把调用方的配置恢复给面板内容，面板里的卡片与按钮会一起没有按压反馈",
            providedValues.any { it != "null" }
        )
    }

    private fun appSourceRoot(): File =
        listOf(File("src/main/java"), File("app/src/main/java"))
            .firstOrNull { it.isDirectory }
            ?: error("找不到源码目录（当前工作目录 ${File("").absolutePath}）")

    private companion object {
        const val WRAPPER_PATH = "com/freshnow/app/ui/component/FreshNowBottomSheet.kt"

        /** 唯一允许直接调用 ModalBottomSheet 的文件 */
        val WRAPPERS = setOf("FreshNowBottomSheet.kt")

        /** 带左括号：避开 import 行，也避开 rememberModalBottomSheetState */
        val MODAL_BOTTOM_SHEET = Regex("""ModalBottomSheet\s*\(""")

        /** 任意空白、任意变量名都认：容器里换行重排或变量改名不算违规 */
        val RIPPLE_PROVISION = Regex("""LocalRippleConfiguration\s+provides\s+(\w+)""")
    }
}
