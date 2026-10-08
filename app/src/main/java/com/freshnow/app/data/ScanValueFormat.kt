package com.freshnow.app.data

import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter

/**
 * 模型返回原文的规范化。
 *
 * 每帧都是一次独立请求，同一个日期可能被写成 `2026-10-06`、`2026/10/6`、`2026年10月06日`、
 * `二〇二六年十月六日`，保质期可能被写成「18个月」「18 个月」「两个月」「一年半」。
 * 而累加规则是「这一帧读到了就覆盖」，于是同一项会因为换了一帧而突然变个写法；
 * 更要紧的是中文数字会让保质期解析失败，导致过期日期算不出来。这里统一成一种写法。
 *
 * 认不出来的原样返回，不做猜测——与过期日期对印刷值的处理保持一致。
 */
object ScanValueFormat {

    private val ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE

    // 阿拉伯数字写的日期：2026-10-06 / 2026/10/6 / 2026年10月6日
    //
    // 前后都不许再连着数字：那是更长的数字串（如 2026-10-123 的日到底是 12 还是 123），
    // 无从判断时宁可认不出，也不能悄悄截掉几位当成日期存下去
    private val SEPARATED_DATE = Regex("""(?<!\d)(\d{4})[-/.年](\d{1,2})[-/.月](\d{1,2})日?(?!\d)""")
    // 紧凑写法：20261006
    private val COMPACT_DATE = Regex("""(?<!\d)(\d{4})(\d{2})(\d{2})(?!\d)""")
    // 中文数字写的日期：二〇二六年十月六日（年份逐位读，月日按十/百规则读）
    private val CHINESE_DATE =
        Regex("""([〇零一二三四五六七八九]{4})年([一二两三四五六七八九十]{1,3})月([一二两三四五六七八九十]{1,3})日?""")

    // 保质期数量：阿拉伯数字、中文数字（一 两 十八 二十四），或「半」
    //
    // 数量前不许再连着数字、小数点或中文数字：那说明这里是更长的一串（1.5个月、-5天、一百二十天），
    // 往后能找到的「5个月」「5天」「二十天」都不是用户写的那个量。与日期同一条规矩，
    // 宁可认不出，也不能把「1.5个月」悄悄当成 5 个月
    private val SHELF_LIFE =
        Regex("""(?<![\d.．+\-−一二两三四五六七八九十百千〇零半])(\d+|[一二两三四五六七八九十]+|半)\s*(个月|月|个星期|星期|个周|周|天|日|年)(半?)""")

    private const val MAX_AMOUNT = 9999.0
    private const val MONTHS_PER_YEAR = 12
    private const val DAYS_PER_WEEK = 7
    private const val HALF = 0.5

    private val CHINESE_DIGITS = mapOf(
        '〇' to 0, '零' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4,
        '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9
    )

    /** 日期统一成 yyyy-MM-dd */
    fun date(raw: String): String = parseDate(raw.trim())?.let(::format) ?: raw

    /** 保质期统一成「数字+单位」，天/日 归到天，月/个月 归到个月，周/星期 也归到天 */
    fun shelfLife(raw: String): String = parseShelfLife(raw)?.let(::formatShelfLife) ?: raw

    internal fun format(date: LocalDate): String = date.format(ISO_DATE)

    internal fun parseDate(text: String): LocalDate? {
        if (text.isEmpty()) return null
        return runCatching { LocalDate.parse(text, ISO_DATE) }.getOrNull()
            ?: SEPARATED_DATE.find(text)?.let(::toDate)
            ?: COMPACT_DATE.find(text)?.let(::toDate)
            ?: CHINESE_DATE.find(text)?.let(::chineseToDate)
    }

    /**
     * 解析保质期。中文数字与「半」都认：标签上「两个月」「半年」「一年半」都很常见；
     * 周也认，酸奶、酸奶类短保商品常写「2周」。
     *
     * 只做有定义的换算：1 年 = 12 个月、1 周 = 7 天，都是恒等式，所以「半年」= 6个月、
     * 「一年半」= 18个月、「2周」= 14天 都能得到唯一结果。
     *
     * 不把月折成天：1 个月是 28~31 天，写成 30 天只是一个假设，「半个月」到底是 14 天还是 15 天
     * 无从判断（真实天数还随生产日期浮动）。凡要靠这类假设才能凑出结果的写法一律返回 null——
     * 与「认不出的原样返回」同一条规矩，宁可让用户改写成「15天」。同一条规矩下，
     * 「半天」「半周」也算不出整天的数，一并返回 null。
     */
    internal fun parseShelfLife(text: String): Period? {
        val match = SHELF_LIFE.find(text.trim()) ?: return null
        val amount = parseAmount(match.groupValues[1]) ?: return null
        val total = amount + if (match.groupValues[3].isEmpty()) 0.0 else HALF
        if (total <= 0 || total > MAX_AMOUNT) return null

        return when (match.groupValues[2]) {
            "年" -> wholeMonths(total * MONTHS_PER_YEAR)
            "个月", "月" -> wholeMonths(total)
            "周", "个周", "星期", "个星期" -> wholeDays(total * DAYS_PER_WEEK)
            "天", "日" -> wholeDays(total)
            else -> null
        }
    }

    private fun wholeMonths(months: Double): Period? =
        if (months % 1.0 == 0.0) Period.ofMonths(months.toInt()) else null

    private fun wholeDays(days: Double): Period? =
        if (days % 1.0 == 0.0) Period.ofDays(days.toInt()) else null

    private fun formatShelfLife(period: Period): String = when {
        period.years != 0 -> "${period.years}年"
        period.months != 0 -> "${period.months}个月"
        else -> "${period.days}天"
    }

    private fun parseAmount(raw: String): Double? = when {
        raw.isEmpty() -> null
        raw.all { it.isDigit() } -> raw.toDouble()
        raw == "半" -> HALF
        else -> parseChineseNumber(raw)
    }

    /** 中文数字，支持 一~九十九（十、十八、二十、二十四），「两」按 2 计 */
    private fun parseChineseNumber(text: String): Double? {
        val tenIndex = text.indexOf('十')
        if (tenIndex >= 0) {
            val tensPart = text.substring(0, tenIndex)
            val unitsPart = text.substring(tenIndex + 1)
            val tens = if (tensPart.isEmpty()) 1 else tensPart.singleDigit() ?: return null
            val units = if (unitsPart.isEmpty()) 0 else unitsPart.singleDigit() ?: return null
            return (tens * 10 + units).toDouble()
        }
        return text.singleDigit()?.toDouble()
    }

    private fun String.singleDigit(): Int? = singleOrNull()?.let { CHINESE_DIGITS[it] }

    private fun chineseToDate(match: MatchResult): LocalDate? {
        val year = match.groupValues[1]
            .map { CHINESE_DIGITS[it] ?: return null }
            .joinToString(separator = "")
            .toIntOrNull() ?: return null
        val month = parseChineseNumber(match.groupValues[2])?.toInt() ?: return null
        val day = parseChineseNumber(match.groupValues[3])?.toInt() ?: return null
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }

    private fun toDate(match: MatchResult): LocalDate? = runCatching {
        LocalDate.of(
            match.groupValues[1].toInt(),
            match.groupValues[2].toInt(),
            match.groupValues[3].toInt()
        )
    }.getOrNull()
}
