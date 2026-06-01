package online.mofish.tool.domain

import java.math.BigDecimal
import java.time.LocalDateTime

data class ForexRate(
    /** 货币中文或数据源展示名称。 */
    val currencyName: String,
    /** ISO 4217 货币代码，例如 USD、HKD。 */
    val currencyCode: String,
    /** 现汇买入价，通常表示银行买入外币现汇的价格。 */
    val spotBuyPrice: BigDecimal?,
    /** 现钞买入价，通常表示银行买入外币现钞的价格。 */
    val cashBuyPrice: BigDecimal?,
    /** 现汇卖出价，通常表示银行卖出外币现汇的价格。 */
    val spotSellPrice: BigDecimal?,
    /** 现钞卖出价，通常表示银行卖出外币现钞的价格。 */
    val cashSellPrice: BigDecimal?,
    /** 折算价或中间参考价，用于收益计算中的币种换算。 */
    val conversionPrice: BigDecimal?,
    /** 牌价发布时间。 */
    val publishedAt: LocalDateTime?,
)
