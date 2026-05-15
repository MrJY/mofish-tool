package online.mofish.tool.domain

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class FundQuote(
    val code: String,
    val name: String,
    val estimatedNetValue: BigDecimal?,
    val previousNetValue: BigDecimal?,
    val dailyChangePercent: BigDecimal?,
    val valuationTime: LocalDateTime?,
    val netValueDate: LocalDate?,
    val status: QuoteStatus = QuoteStatus.TRADING,
    val isEstimated: Boolean = true,
)
