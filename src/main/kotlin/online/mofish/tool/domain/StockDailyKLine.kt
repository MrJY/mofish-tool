package online.mofish.tool.domain

import java.math.BigDecimal
import java.time.LocalDate

data class StockDailyKLine(
    /** 当前 K 线交易日。 */
    val date: LocalDate,
    /** 开盘价。 */
    val open: BigDecimal,
    /** 收盘价。 */
    val close: BigDecimal,
    /** 最高价。 */
    val high: BigDecimal,
    /** 最低价。 */
    val low: BigDecimal,
    /** 成交量。 */
    val volume: BigDecimal?,
)
