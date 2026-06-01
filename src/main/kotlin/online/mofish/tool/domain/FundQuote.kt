package online.mofish.tool.domain

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class FundQuote(
    /** 基金代码。 */
    val code: String,
    /** 基金名称。 */
    val name: String,
    /** 盘中估算净值，可能为空。 */
    val estimatedNetValue: BigDecimal?,
    /** 上一个确认净值。 */
    val previousNetValue: BigDecimal?,
    /** 当日涨跌幅百分比。 */
    val dailyChangePercent: BigDecimal?,
    /** 估值更新时间。 */
    val valuationTime: LocalDateTime?,
    /** 确认净值对应的日期。 */
    val netValueDate: LocalDate?,
    /** 行情可用状态。 */
    val status: QuoteStatus = QuoteStatus.TRADING,
    /** 当前净值是否来自盘中估算。 */
    val isEstimated: Boolean = true,
)
