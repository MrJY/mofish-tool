package online.mofish.tool.domain

import java.math.BigDecimal
import java.time.LocalDateTime

data class StockQuote(
    val code: String,
    val name: String,
    val symbol: String,
    val exchange: StockExchange,
    val currentPrice: BigDecimal?,
    val previousClose: BigDecimal?,
    val openPrice: BigDecimal?,
    val highPrice: BigDecimal?,
    val lowPrice: BigDecimal?,
    val changeAmount: BigDecimal?,
    val changePercent: BigDecimal?,
    val volume: BigDecimal?,
    val turnover: BigDecimal?,
    val updatedAt: LocalDateTime?,
    val status: QuoteStatus = QuoteStatus.TRADING,
    val afterHoursPrice: BigDecimal? = null,
    val afterHoursChangePercent: BigDecimal? = null,
)
