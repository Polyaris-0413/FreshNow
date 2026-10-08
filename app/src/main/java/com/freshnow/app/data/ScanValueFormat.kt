package com.freshnow.app.data

import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter

/**
 * 模型返回原文的规范化。
 *
 * 每帧都是一次独立请求，同一个日期可能被写成 `2026-10-06`、`2026/10/6`、`2026年10月06日`、
 * `二〇二六年十月六日`，保质期可能被写成「18个月」「18 个月」「两个月」「15 天」。
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

    // 保质期数量：阿拉伯数字或中文数字（一 两 十八 二十四），不收「半」——只认整数
    //
    // 数量前不许再连着数字、小数点或中文数字：那说明这里是更长的一串（1.5个月、-5天、一百二十天），
    // 往后能找到的「5个月」「5天」「二十天」都不是用户写的那个量。末尾同理，后面跟着「半」的也不认，
    // 否则「一年半」会被读成「一年」。复合写法（1年6个月、18个月零3天）由 [parseShelfLife] 的「只认一段」拦下——
    // 「零」不能算在这个集合里，否则「零3天」不算第二段，复合反而漏网。
    // 与日期同一条规矩：宁可认不出，也不能少算
    private val SHELF_LIFE =
        Regex("""(?<![\d.．\-−一二两三四五六七八九十百千半])(\d+|[一二两三四五六七八九十]+)\s*(个月|月|个星期|星期|个周|周|天|日|年)(?!半)""")

    private const val MAX_AMOUNT = 9999
    private const val MONTHS_PER_YEAR = 12
    private const val DAYS_PER_WEEK = 7

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
     * 解析保质期。只认整数，写出来就是一句话：**整数 + 年 / 个月 / 天 / 周**（如 18个月、180天、1年、2周）。
     * 中文数字（两个月、十八个月）与 日 / 星期 / 个周 这类同义写法照样认，但不必让用户知道——
     * 它们零歧义，只是同一个意思的另一种写法。
     *
     * 不收「半」：半年、一年半、半个月、半天、半周一律返回 null。半年、一年半本身换算得出（6、18 个月），
     * 但留着它们就得解释「半在年后面可以、在月后面不行」，而那恰恰是最难记的一条；收窄成整数，
     * 规则才有一句话的余地，用户写「半年」时改成「6个月」即可。
     *
     * 也不把月折成天：1 个月是 28~31 天，写成 30 天只是一个假设。靠假设才能凑出结果的一律返回 null——
     * 与「认不出的原样返回」同一条规矩。
     *
     * 还要求整串里恰好只有一段数量+单位，所以复合成两个量的写法一律返回 null。
     */
    internal fun parseShelfLife(text: String): Period? {
        // 只认一段。复合写法（1年6个月、一年三个月、18个月零3天）会被拆成两段以上，一律不接受——
        // 不求和是因为「6个月(180天)」这类主值 + 换算说明也会拆成两段，把两段叠起来不是同一件事，
        // 而仅靠分隔符分不出这两种情况。宁可让用户只写一个量，也不猜他的意思
        val match = SHELF_LIFE.findAll(text.trim()).singleOrNull() ?: return null
        val amount = parseAmount(match.groupValues[1]) ?: return null
        if (amount <= 0 || amount > MAX_AMOUNT) return null

        return when (match.groupValues[2]) {
            "年" -> Period.ofMonths(amount * MONTHS_PER_YEAR)
            "个月", "月" -> Period.ofMonths(amount)
            "周", "个周", "星期", "个星期" -> Period.ofDays(amount * DAYS_PER_WEEK)
            "天", "日" -> Period.ofDays(amount)
            else -> null
        }
    }

    private fun formatShelfLife(period: Period): String = when {
        period.years != 0 -> "${period.years}年"
        period.months != 0 -> "${period.months}个月"
        else -> "${period.days}天"
    }

    /** 数量：阿拉伯数字或中文数字。两者都是整数，所以直接是 Int；超出 Int 范围的一律认不出 */
    private fun parseAmount(raw: String): Int? = when {
        raw.isEmpty() -> null
        raw.all { it.isDigit() } -> raw.toIntOrNull()
        else -> parseChineseNumber(raw)
    }

    /** 中文数字，支持 一~九十九（十、十八、二十、二十四），「两」按 2 计 */
    private fun parseChineseNumber(text: String): Int? {
        val tenIndex = text.indexOf('十')
        if (tenIndex >= 0) {
            val tensPart = text.substring(0, tenIndex)
            val unitsPart = text.substring(tenIndex + 1)
            val tens = if (tensPart.isEmpty()) 1 else tensPart.singleDigit() ?: return null
            val units = if (unitsPart.isEmpty()) 0 else unitsPart.singleDigit() ?: return null
            return tens * 10 + units
        }
        return text.singleDigit()
    }

    private fun String.singleDigit(): Int? = singleOrNull()?.let { CHINESE_DIGITS[it] }

    private fun chineseToDate(match: MatchResult): LocalDate? {
        val year = match.groupValues[1]
            .map { CHINESE_DIGITS[it] ?: return null }
            .joinToString(separator = "")
            .toIntOrNull() ?: return null
        val month = parseChineseNumber(match.groupValues[2]) ?: return null
        val day = parseChineseNumber(match.groupValues[3]) ?: return null
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
