package online.mofish.tool.domain

import java.math.BigDecimal
import java.time.LocalDateTime

data class CryptoQuote(
    val code: String,
    val symbol: String,
    val name: String,
    val currentPrice: BigDecimal?,
    val marketCap: BigDecimal?,
    val totalVolume: BigDecimal?,
    val priceChangePercentage24h: BigDecimal?,
    val circulatingSupply: BigDecimal?,
    val updatedAt: LocalDateTime?,
    val quoteCurrency: String = "USD",
    val status: QuoteStatus = QuoteStatus.TRADING,
)
