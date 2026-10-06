package com.freshnow.app.data

import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter

/**
 * 模型返回原文的规范化。
 *
 * 每帧都是一次独立请求，同一个日期可能被写成 `2026-10-06`、`2026/10/6`、`2026年10月06日`，
 * 保质期可能被写成「18个月」「18 个月」「2月」。而累加规则是「这一帧读到了就覆盖」，
 * 于是同一项会因为换了一帧而突然变个写法。这里统一成一种写法，界面与落库的值就稳定了。
 *
 * 认不出来的原样返回，不做猜测——与过期日期对印刷值的处理保持一致。
 */
object ScanValueFormat {

    private val ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE
    private val SEPARATED_DATE = Regex("""(\d{4})[-/.年](\d{1,2})[-/.月](\d{1,2})日?""")
    private val COMPACT_DATE = Regex("""(\d{4})(\d{2})(\d{2})""")
    private val SHELF_LIFE = Regex("""(\d+)\s*(个月|月|天|日|年)""")

    private const val MAX_SHELF_LIFE_AMOUNT = 9999

    /** 日期统一成 yyyy-MM-dd */
    fun date(raw: String): String = parseDate(raw.trim())?.let(::format) ?: raw

    /** 保质期统一成「数字+单位」，天/日 归到天，月/个月 归到个月 */
    fun shelfLife(raw: String): String {
        val match = SHELF_LIFE.find(raw.trim()) ?: return raw
        val amount = match.groupValues[1].toIntOrNull()
            ?.takeIf { it in 1..MAX_SHELF_LIFE_AMOUNT }
            ?: return raw
        val unit = when (match.groupValues[2]) {
            "天", "日" -> "天"
            "个月", "月" -> "个月"
            "年" -> "年"
            else -> return raw
        }
        return "$amount$unit"
    }

    internal fun format(date: LocalDate): String = date.format(ISO_DATE)

    internal fun parseDate(text: String): LocalDate? {
        if (text.isEmpty()) return null
        return runCatching { LocalDate.parse(text, ISO_DATE) }.getOrNull()
            ?: SEPARATED_DATE.find(text)?.let(::toDate)
            ?: COMPACT_DATE.find(text)?.let(::toDate)
    }

    internal fun parseShelfLife(text: String): Period? {
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

    private fun toDate(match: MatchResult): LocalDate? = runCatching {
        LocalDate.of(
            match.groupValues[1].toInt(),
            match.groupValues[2].toInt(),
            match.groupValues[3].toInt()
        )
    }.getOrNull()
}
