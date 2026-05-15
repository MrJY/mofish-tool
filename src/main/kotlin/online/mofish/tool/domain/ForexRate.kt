package online.mofish.tool.domain

import java.math.BigDecimal
import java.time.LocalDateTime

data class ForexRate(
    val currencyName: String,
    val currencyCode: String,
    val spotBuyPrice: BigDecimal?,
    val cashBuyPrice: BigDecimal?,
    val spotSellPrice: BigDecimal?,
    val cashSellPrice: BigDecimal?,
    val conversionPrice: BigDecimal?,
    val publishedAt: LocalDateTime?,
)
