package com.freshnow.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ScanValueFormatTest {

    @Test
    fun date_alreadyIso_isUnchanged() {
        assertEquals("2026-10-06", ScanValueFormat.date("2026-10-06"))
    }

    /** 标签上印的是中文年月日，模型有时照抄，这里统一回 ISO */
    @Test
    fun date_withChineseUnits_isNormalizedAndPadded() {
        assertEquals("2026-10-06", ScanValueFormat.date("2026年10月06日"))
        assertEquals("2026-10-06", ScanValueFormat.date("2026年10月6日"))
    }

    @Test
    fun date_withSlashesAndSingleDigits_isPadded() {
        assertEquals("2026-10-06", ScanValueFormat.date("2026/10/6"))
        // 模型照抄标签简写时会出现这种不补零的写法
        assertEquals("2026-10-06", ScanValueFormat.date("2026-10-6"))
        assertEquals("2026-01-06", ScanValueFormat.date("2026-1-6"))
    }

    @Test
    fun date_compact_isNormalized() {
        assertEquals("2026-10-06", ScanValueFormat.date("20261006"))
    }

    @Test
    fun date_withSurroundingText_keepsOnlyDate() {
        assertEquals("2026-10-06", ScanValueFormat.date("生产日期 2026-10-06"))
    }

    /** 连数字都用中文写的日期，标签上确实存在 */
    @Test
    fun date_writtenInChineseNumerals_isNormalized() {
        assertEquals("2026-10-06", ScanValueFormat.date("二〇二六年十月六日"))
        assertEquals("2026-12-24", ScanValueFormat.date("二〇二六年十二月二十四日"))
    }

    @Test
    fun date_unparseable_isKeptAsIs() {
        assertEquals("见包装喷码", ScanValueFormat.date("见包装喷码"))
    }

    /**
     * 数字连成一长串时不敢认：`2026-10-123` 的日到底是 12 还是 123 无从判断。
     * 旧规则会把它读成 12、偷偷截掉末尾，这比认不出更糟——存下去的东西与用户写的不是一个日期
     */
    @Test
    fun date_withTooManyDigits_isKeptAsIs() {
        assertEquals("2026-10-123", ScanValueFormat.date("2026-10-123"))
        assertEquals("202610123", ScanValueFormat.date("202610123"))
        // 年份前面粘了数字同样不敢认，否则会被读成公元 260 年
        assertEquals("12026-10-06", ScanValueFormat.date("12026-10-06"))
    }

    @Test
    fun blankDate_staysBlank() {
        assertEquals("", ScanValueFormat.date(""))
    }

    @Test
    fun shelfLife_withSpace_isNormalized() {
        assertEquals("18个月", ScanValueFormat.shelfLife("18 个月"))
    }

    @Test
    fun shelfLife_singleMonthChar_becomesMonths() {
        assertEquals("2个月", ScanValueFormat.shelfLife("2月"))
    }

    @Test
    fun shelfLife_dayVariants_becomeDays() {
        assertEquals("180天", ScanValueFormat.shelfLife("180日"))
        assertEquals("180天", ScanValueFormat.shelfLife("180 天"))
    }

    /** 中文数字的保质期：这曾经让过期日期直接算不出来 */
    @Test
    fun shelfLife_chineseNumerals_areConverted() {
        assertEquals("2个月", ScanValueFormat.shelfLife("两个月"))
        assertEquals("2个月", ScanValueFormat.shelfLife("二个月"))
        assertEquals("10天", ScanValueFormat.shelfLife("十天"))
        assertEquals("18个月", ScanValueFormat.shelfLife("十八个月"))
        assertEquals("20天", ScanValueFormat.shelfLife("二十天"))
        assertEquals("24个月", ScanValueFormat.shelfLife("二十四个月"))
    }

    /**
     * 「半」一律不认：只认整数，规则才有一句话的余地（整数 + 年 / 个月 / 天 / 周）。
     * 半年、一年半换算得出（6、18 个月），但留着它们就得解释「半在年后面可以、在月后面不行」，
     * 而半个月这类本就要靠「1个月=30天」的假设
     */
    @Test
    fun shelfLife_half_isNotAccepted() {
        assertEquals("半年", ScanValueFormat.shelfLife("半年"))
        assertEquals("一年半", ScanValueFormat.shelfLife("一年半"))
        assertEquals("半个月", ScanValueFormat.shelfLife("半个月"))
        assertEquals("1个月半", ScanValueFormat.shelfLife("1个月半"))
        assertEquals("1个半月", ScanValueFormat.shelfLife("1个半月"))
        assertEquals("两个半月", ScanValueFormat.shelfLife("两个半月"))
        assertEquals("半天", ScanValueFormat.shelfLife("半天"))
        assertEquals("半周", ScanValueFormat.shelfLife("半周"))
    }

    /**
     * 数值前还粘着东西时不敢认：`1.5个月` 里往后能找到 `5个月`、`-5天` 里能找到 `5天`，
     * 但那都不是用户写的量。与日期同一条规矩（见 date_withTooManyDigits_isKeptAsIs）
     */
    @Test
    fun shelfLife_withLeadingNumericNoise_isKeptAsIs() {
        assertEquals("1.5个月", ScanValueFormat.shelfLife("1.5个月"))
        assertEquals("-5天", ScanValueFormat.shelfLife("-5天"))
        assertEquals("一百二十天", ScanValueFormat.shelfLife("一百二十天"))
    }

    /**
     * 复合写法不认：整串里出现两段以上数量+单位（1年6个月、一年三个月、18个月零3天）一律判为认不出。
     * 不做求和，是因为「6个月(180天)」这类主值 + 换算说明同样是两段，靠分隔符分不出两者
     */
    @Test
    fun shelfLife_compound_isNotAccepted() {
        assertEquals("1年6个月", ScanValueFormat.shelfLife("1年6个月"))
        assertEquals("一年三个月", ScanValueFormat.shelfLife("一年三个月"))
        assertEquals("18个月零3天", ScanValueFormat.shelfLife("18个月零3天"))
        // 主值 + 括号里的换算说明也是两段，一并拒掉（与上面无法区分）
        assertEquals("6个月(180天)", ScanValueFormat.shelfLife("6个月(180天)"))
        // 一段不受影响：前面有文字也不会被当成第二段
        assertEquals("18个月", ScanValueFormat.shelfLife("常温下保质期18个月"))
    }

    /** 酸奶、面包这类短保标签常写「周」，归到天 */
    @Test
    fun shelfLife_weeks_becomeDays() {
        assertEquals("7天", ScanValueFormat.shelfLife("1周"))
        assertEquals("14天", ScanValueFormat.shelfLife("2周"))
        assertEquals("14天", ScanValueFormat.shelfLife("2 个星期"))
        assertEquals("14天", ScanValueFormat.shelfLife("两个星期"))
    }

    /** 端到端：周也能算出过期日期 */
    @Test
    fun weekShelfLife_producesExpiryDate() {
        assertEquals(
            ExpiryOutcome.Resolved("2026-10-20"),
            ExpiryCalculator.resolve(printedExpiry = "", productionDate = "2026-10-06", shelfLife = "2周")
        )
    }

    @Test
    fun shelfLife_unparseable_isKeptAsIs() {
        assertEquals("见包装", ScanValueFormat.shelfLife("见包装"))
    }

    /** 端到端：中文数字的保质期也能算出过期日期 */
    @Test
    fun chineseShelfLife_producesExpiryDate() {
        assertEquals(
            ExpiryOutcome.Resolved("2026-12-06"),
            ExpiryCalculator.resolve(printedExpiry = "", productionDate = "2026-10-06", shelfLife = "两个月")
        )
    }
}
