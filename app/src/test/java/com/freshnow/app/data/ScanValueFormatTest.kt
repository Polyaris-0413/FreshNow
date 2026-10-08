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

    /** 前后夹着别的字不再从里面挑：整串必须就是一个日期，挑出来的可能不是用户写的那个 */
    @Test
    fun date_withSurroundingText_isNotAccepted() {
        assertEquals("生产日期 2026-10-06", ScanValueFormat.date("生产日期 2026-10-06"))
        assertEquals("2026-10-06 2027-01-01", ScanValueFormat.date("2026-10-06 2027-01-01"))
    }

    /** 同一个日期的等价写法都认（分隔符不同、不补零、缺「日」、中文字法），都是零歧义的同一件事 */
    @Test
    fun date_equivalentWritings_areAccepted() {
        listOf(
            "2026-10-6", "2026/10/6", "2026.10.6", "2026年10月6日", "2026年10月6", "20261006",
            "二〇二六年十月六日", "二〇二六年十月六"
        ).forEach {
            assertEquals(it, "2026-10-06", ScanValueFormat.date(it))
        }
        assertEquals("2024-02-29", ScanValueFormat.date("2024-02-29"))
    }

    /**
     * 整串读不成一个合法日期的一律拒。[date_equivalentWritings_areAccepted] 认的那些是写法不同，
     * 这里拒的是根本无法成立：没有那一天、少一段、多粘了数字、夹着别的东西
     */
    @Test
    fun date_unusableWritings_areKeptAsIs() {
        listOf(
            // 没有那一天
            "2023-02-29", "2026-02-30", "2026-04-31", "2026-13-01", "2026-00-10", "2026-10-00",
            "二〇二六年二月三十日", "二〇二六年十三月一日",
            // 少一段
            "2026-10", "2026年10月", "2026年10", "2026106", "26-10-06",
            // 数字粘成一串
            "2026-10-123", "12026-10-06", "202610123", "2026-10-061",
            // 夹着别的东西
            "2026-10-06，", "2026-10-06T00:00", "2026年10月6日 到期", "+2026-10-06"
        ).forEach {
            assertEquals(it, ScanValueFormat.date(it))
        }
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
     * 整串必须恰好是一个量，多一个字都认不出。下面这些以前都被静默读成了其中一部分：
     * 1.5个月 → 5个月、-5天 → 5天、两个半月 → 15天、一年三 → 一年。
     *
     * 全列在一处，是因为它们不是几条不同的规则，而是同一条：整串能不能被「数量+单位」吃完
     */
    @Test
    fun shelfLife_mustBeExactlyOneAmount() {
        listOf(
            // 数值前还粘着东西
            "1.5个月", "-5天", "一百二十天",
            // 「半」
            "半年", "一年半", "半个月", "半天", "半周", "1个月半", "1个半月", "两个半月",
            // 复合写法，或只写了半截
            "1年6个月", "一年三", "一年三个", "一年三个月", "18个月零3天",
            // 前后夹着别的文字
            "6个月(180天)", "保质期18个月", "常温下保质期18个月"
        ).forEach {
            assertEquals(it, ScanValueFormat.shelfLife(it))
        }
    }

    /** 数量的上下限：0 与超出上限的拒，限内的认 */
    @Test
    fun shelfLife_amountBounds_areEnforced() {
        assertEquals("9999天", ScanValueFormat.shelfLife("9999天"))
        assertEquals("9999个月", ScanValueFormat.shelfLife("9999个月"))
        listOf("0天", "0个月", "10000天", "10000个月").forEach {
            assertEquals(it, ScanValueFormat.shelfLife(it))
        }
    }

    /** 读不成那个量的其它写法：繁体、全角、字法不成立、尾随标点、数字或单位被断开 */
    @Test
    fun shelfLife_unusableWritings_areKeptAsIs() {
        listOf(
            "18個月", "１８个月",
            "18个月！", "18个月。",
            "1 8个月", "18个 月",
            "十十个月", "九九个月", "〇个月", "一百个月",
            // 保质期字段里填成了日期
            "2026-10-06"
        ).forEach {
            assertEquals(it, ScanValueFormat.shelfLife(it))
        }
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
