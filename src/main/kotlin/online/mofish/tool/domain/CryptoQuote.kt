package online.mofish.tool.domain

import java.math.BigDecimal
import java.time.LocalDateTime

data class CryptoQuote(
    /** 虚拟币在数据源中的唯一标识，通常对应 CoinGecko ID。 */
    val code: String,
    /** 虚拟币交易符号，例如 BTC、ETH。 */
    val symbol: String,
    /** 虚拟币展示名称。 */
    val name: String,
    /** 当前报价，按 quoteCurrency 表示。 */
    val currentPrice: BigDecimal?,
    /** 当前总市值，按 quoteCurrency 表示。 */
    val marketCap: BigDecimal?,
    /** 最近 24 小时成交额，按 quoteCurrency 表示。 */
    val totalVolume: BigDecimal?,
    /** 最近 24 小时价格涨跌幅百分比。 */
    val priceChangePercentage24h: BigDecimal?,
    /** 当前流通供应量。 */
    val circulatingSupply: BigDecimal?,
    /** 行情数据最后更新时间。 */
    val updatedAt: LocalDateTime?,
    /** 报价币种，默认使用美元。 */
    val quoteCurrency: String = "USD",
    /** 行情可用状态。 */
    val status: QuoteStatus = QuoteStatus.TRADING,
)
