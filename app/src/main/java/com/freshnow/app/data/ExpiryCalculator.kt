package com.freshnow.app.data

import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter

sealed interface ExpiryOutcome {
    /** 已得到明确日期：标签印刷值，或由生产日期与保质期推算而来 */
    data class Resolved(val date: String) : ExpiryOutcome

    /** 缺少生产日期或保质期，无从推算 */
    data object InsufficientInput : ExpiryOutcome

    /** 生产日期与保质期都有，但保质期格式无法解析 */
    data object UnparseableShelfLife : ExpiryOutcome
}

/**
 * 过期日期的唯一来源：只在标签没印刷过期日期时才用 生产日期 + 保质期 推算。
 * 日期运算交给 java.time（月末、闰年由 JDK 保证），解析不出来就如实返回失败，不做任何猜测。
 */
object ExpiryCalculator {

    private val ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE
    private val SEPARATED_DATE = Regex("""(\d{4})[-/.年](\d{1,2})[-/.月](\d{1,2})日?""")
    private val COMPACT_DATE = Regex("""(\d{4})(\d{2})(\d{2})""")
    private val SHELF_LIFE = Regex("""(\d+)\s*(个月|月|天|日|年)""")

    private const val MAX_SHELF_LIFE_AMOUNT = 9999

    fun resolve(printedExpiry: String, productionDate: String, shelfLife: String): ExpiryOutcome {
        val printed = printedExpiry.trim()
        if (printed.isNotEmpty()) {
            // 印刷值能规范化就规范化，不能就按原文展示——绝不覆盖印刷值
            return ExpiryOutcome.Resolved(parseDate(printed)?.format(ISO_DATE) ?: printed)
        }

        val produced = parseDate(productionDate.trim()) ?: return ExpiryOutcome.InsufficientInput
        val duration = parseShelfLife(shelfLife) ?: return ExpiryOutcome.UnparseableShelfLife
        val expiry = runCatching { produced.plus(duration) }.getOrNull()
            ?: return ExpiryOutcome.UnparseableShelfLife

        return ExpiryOutcome.Resolved(expiry.format(ISO_DATE))
    }

    private fun parseDate(text: String): LocalDate? {
        if (text.isEmpty()) return null
        return runCatching { LocalDate.parse(text, ISO_DATE) }.getOrNull()
            ?: SEPARATED_DATE.find(text)?.let { toDate(it) }
            ?: COMPACT_DATE.find(text)?.let { toDate(it) }
    }

    private fun toDate(match: MatchResult): LocalDate? = runCatching {
        LocalDate.of(
            match.groupValues[1].toInt(),
            match.groupValues[2].toInt(),
            match.groupValues[3].toInt()
        )
    }.getOrNull()

    private fun parseShelfLife(text: String): Period? {
        val match = SHELF_LIFE.find(text.trim()) ?: return null
        val amount = match.groupValues[1].toIntOrNull()
            ?.takeIf { it in 1..MAX_SHELF_LIFE_AMOUNT }
            ?: return null

        return when (match.groupValues[2]) {
            "天", "日" -> Period.ofDays(amount)
            "个月", "月" -> Period.ofMonths(amount)
            "年" -> Period.ofYears(amount)
            else -> null
        }
    }
}
