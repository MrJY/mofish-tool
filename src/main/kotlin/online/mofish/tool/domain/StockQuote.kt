package online.mofish.tool.domain

import java.math.BigDecimal
import java.time.LocalDateTime

data class StockQuote(
    /** 股票或指数在应用中的代码。 */
    val code: String,
    /** 股票或指数名称。 */
    val name: String,
    /** 行情接口使用或展示的标准化代码。 */
    val symbol: String,
    /** 股票或指数所属交易所。 */
    val exchange: StockExchange,
    /** 当前价格或指数点位。 */
    val currentPrice: BigDecimal?,
    /** 前收盘价。 */
    val previousClose: BigDecimal?,
    /** 今日开盘价。 */
    val openPrice: BigDecimal?,
    /** 今日最高价。 */
    val highPrice: BigDecimal?,
    /** 今日最低价。 */
    val lowPrice: BigDecimal?,
    /** 相对前收盘价的涨跌额。 */
    val changeAmount: BigDecimal?,
    /** 相对前收盘价的涨跌幅百分比。 */
    val changePercent: BigDecimal?,
    /** 成交量。 */
    val volume: BigDecimal?,
    /** 成交额。 */
    val turnover: BigDecimal?,
    /** 行情更新时间。 */
    val updatedAt: LocalDateTime?,
    /** 行情可用状态。 */
    val status: QuoteStatus = QuoteStatus.TRADING,
    /** 盘后价格，美股等支持盘后行情的市场可能有值。 */
    val afterHoursPrice: BigDecimal? = null,
    /** 盘后涨跌幅百分比。 */
    val afterHoursChangePercent: BigDecimal? = null,
)
