package online.mofish.tool.domain

import java.math.BigDecimal
import java.time.LocalDateTime

data class StockIntradayPoint(
    /** 分时点对应时间。 */
    val time: LocalDateTime,
    /** 当前成交价。 */
    val price: BigDecimal,
    /** 分时均价，数据源未返回时为空。 */
    val averagePrice: BigDecimal?,
    /** 当前分钟成交量。 */
    val volume: BigDecimal?,
)
