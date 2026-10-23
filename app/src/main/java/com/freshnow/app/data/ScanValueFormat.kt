package com.freshnow.app.data

import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter

/**
 * 每帧都是独立请求，同一项会换着写法回来（`2026/10/6`、`二〇二六年十月六日`、「18 个月」），
 * 而累加规则是「这一帧读到了就覆盖」；中文数字还会让保质期算不出过期日期。
 * 认不出来的原样返回，不做猜测——与过期日期对印刷值的处理一致。
 */
object ScanValueFormat {

    private val ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE

    // 日期不写 ^ $ 与前后断言：整串由 [parseDate] 的 matchEntire 保证，边界只有这一个来源。
    // 阿拉伯数字写的日期：2026-10-06 / 2026/10/6 / 2026年10月6日
    private val SEPARATED_DATE = Regex("""(\d{4})[-/.年](\d{1,2})[-/.月](\d{1,2})日?""")
    private val COMPACT_DATE = Regex("""(\d{4})(\d{2})(\d{2})""")
    // 中文数字写的日期：二〇二六年十月六日（年份逐位读，月日按十/百规则读）
    private val CHINESE_DATE =
        Regex("""([〇零一二三四五六七八九]{4})年([一二两三四五六七八九十]{1,3})月([一二两三四五六七八九十]{1,3})日?""")

    // 保质期：阿拉伯数字或中文数字（一 两 十八 二十四）+ 单位，整串恰好就是一个这样的量，不收「半」。
    // 边界只由 [parseShelfLife] 的 matchEntire 给出（不写前后断言）：吃不干净的一律认不出，
    // 不必为每种残留各写一条检查。与日期同一条规矩：宁可认不出，也不能少算。
    private val SHELF_LIFE =
        Regex("""(\d+|[一二两三四五六七八九十]+)\s*(个月|月|个星期|星期|个周|周|天|日|年)""")

    private const val MAX_AMOUNT = 9999
    private const val MONTHS_PER_YEAR = 12
    private const val DAYS_PER_WEEK = 7

    /**
     * 这是一类字符（标点），不是一列词：列词就永远说不清生效的是规则还是词表，遇到没列进去的
     * 写法就失效；标点作为一个字符类是封闭的，跟本文件里其它正则字符类同一种东西。
     */
    private const val SURROUNDING_PUNCTUATION =
        "。，、；：？！…～·—「」『』《》【】（）“”‘’\"'.,;:!?()"

    private val CHINESE_DIGITS = mapOf(
        '〇' to 0, '零' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4,
        '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9
    )

    fun date(raw: String): String = parseDate(raw.trim())?.let(::format) ?: raw

    /**
     * 品名是四项里唯一没有固定写法的（日期能解析成一种形式、保质期能收成「整数 + 单位」），
     * 所以这里只能做这一步：模型多打一个空格、或照抄时末尾带一个句号，在累加规则里就会被当成
     * 「换了个值」——而界面上那一下回执是给用户看的，为空格闪一次就是假消息。
     *
     * 只动首尾，不动中间：中文品名里本不该有空格，但拉丁品名有，删了会粘成一个词。
     */
    fun text(raw: String): String = raw.trim { it.isWhitespace() || it in SURROUNDING_PUNCTUATION }

    /** 保质期统一成「数字+单位」，天/日 归到天，月/个月 归到个月，周/星期 也归到天 */
    fun shelfLife(raw: String): String = parseShelfLife(raw)?.let(::formatShelfLife) ?: raw

    internal fun format(date: LocalDate): String = date.format(ISO_DATE)

    /**
     * 整串必须恰好就是一个日期，这一段以外的字一律不算数。
     *
     * 写法的种类可以有好几种（阿拉伯数字、斜杠、中文年月日、紧凑、中文数字），但都是同一个日期的
     * 等价写法，零歧义；而从一句话里挑出看着像日期的几个字则不是，挑出来的可能不是用户写的那个日期
     * （2026-10-06 2027-01-01 该算哪一个）。与保质期同一条规矩：宁可认不出，也不能少算。
     */
    internal fun parseDate(text: String): LocalDate? {
        val raw = text.trim()
        if (raw.isEmpty()) return null
        return runCatching { LocalDate.parse(raw, ISO_DATE) }.getOrNull()
            ?: SEPARATED_DATE.matchEntire(raw)?.let(::toDate)
            ?: COMPACT_DATE.matchEntire(raw)?.let(::toDate)
            ?: CHINESE_DATE.matchEntire(raw)?.let(::chineseToDate)
    }

    /**
     * 只认整数：**整数 + 年 / 个月 / 天 / 周**（如 18个月、180天、1年、2周）。
     * 中文数字（两个月、十八个月）与 日 / 星期 / 个周 这类同义写法照样认——它们零歧义。
     * 不收「半」：半年、一年半、半个月、半天、半周一律返回 null。半年、一年半本身换算得出，
     * 但留着它们就得解释「半在年后面可以、在月后面不行」；收窄成整数，规则才有一句话的余地。
     * 也不把月折成天：1 个月是 28~31 天，写成 30 天只是一个假设——靠假设才能凑出结果的一律返回 null。
     * 还要求整串恰好就是一个量：1.5个月、一年三、一年三个月、6个月(180天) 都不认，
     * 不从一串话里挑出看着像量的几个字——带前后缀的原文会原样保留并显示「推不出」。
     */
    internal fun parseShelfLife(text: String): Period? {
        val match = SHELF_LIFE.matchEntire(text.trim()) ?: return null
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

    /** 两者都是整数，所以直接是 Int；超出 Int 范围的一律认不出 */
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

/**
 * 空白不算错：模型没读到、用户还没填都是正常的，
 * 而「写了东西但认不出」要挡住保存（见两处记录表单的 canSave）。
 *
 * 与解析共用同一份规则（[ScanValueFormat]），不另写一份：两份判据分家之后，
 * 会出现「界面上报认得、保存时却算不出日期」这种自相矛盾。
 */
internal fun isUnrecognizableDate(raw: String): Boolean =
    raw.isNotBlank() && ScanValueFormat.parseDate(raw.trim()) == null

/** 同上，保质期那一项 */
internal fun isUnrecognizableShelfLife(raw: String): Boolean =
    raw.isNotBlank() && ScanValueFormat.parseShelfLife(raw.trim()) == null
