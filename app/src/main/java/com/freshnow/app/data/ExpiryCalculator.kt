package com.freshnow.app.data

import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit

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
 *
 * 日期与保质期的“认得出/认不出”由 [ScanValueFormat] 负责，这里只管运算，避免两处各写一套解析。
 */
object ExpiryCalculator {

    fun resolve(printedExpiry: String, productionDate: String, shelfLife: String): ExpiryOutcome {
        val printed = printedExpiry.trim()
        if (printed.isNotEmpty()) {
            // 印刷值能规范化就规范化，不能就按原文展示——绝不覆盖印刷值
            return ExpiryOutcome.Resolved(ScanValueFormat.date(printed))
        }

        val produced = ScanValueFormat.parseDate(productionDate.trim())
            ?: return ExpiryOutcome.InsufficientInput
        val duration = ScanValueFormat.parseShelfLife(shelfLife)
            ?: return ExpiryOutcome.UnparseableShelfLife
        val expiry = runCatching { produced.plus(duration) }.getOrNull()
            ?: return ExpiryOutcome.UnparseableShelfLife

        return ExpiryOutcome.Resolved(ScanValueFormat.format(expiry))
    }

    /**
     * 距过期还有多少天：正数表示还没过期，0 表示当天到期，负数表示已经过期。
     *
     * 过期日期认不出来（推算不出来，或印刷值根本不是日期）时返回 null，怎么说明由界面决定。
     * [today] 由调用方传入——「今天」是运行时取的值，不在这里固化。
     */
    fun daysRemaining(expiry: ExpiryOutcome, today: LocalDate): Long? {
        val date = (expiry as? ExpiryOutcome.Resolved)
            ?.let { ScanValueFormat.parseDate(it.date.trim()) }
            ?: return null
        return ChronoUnit.DAYS.between(today, date)
    }
}
