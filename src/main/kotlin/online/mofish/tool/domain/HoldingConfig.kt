package online.mofish.tool.domain

import java.math.BigDecimal
import java.io.Serializable

data class HoldingConfig(
    /** 持仓配置唯一标识，用于编辑、删除和收益快照关联。 */
    val id: String,
    /** 持仓所属资产类型。 */
    val assetType: AssetType,
    /** 持仓资产代码。 */
    val code: String,
    /** 持仓在界面中展示的名称。 */
    val displayName: String,
    /** 持有金额，适用于基金等按金额记录的资产。 */
    val investedAmount: BigDecimal? = null,
    /** 持有数量或份额，适用于股票、基金和虚拟币。 */
    val quantity: BigDecimal? = null,
    /** 持仓成本价或单位成本。 */
    val costPrice: BigDecimal,
    /** 当日成本价，用于计算日内盈亏，没有单独设置时为空。 */
    val todayCostPrice: BigDecimal? = null,
    /** 持仓计价币种，默认人民币。 */
    val currency: String = "CNY",
    /** 是否已经清仓，清仓持仓不再计入有效持仓数量。 */
    val isSellOut: Boolean = false,
) : Serializable {
    /** 当前持仓是否仍有有效仓位。 */
    val hasPosition: Boolean
        get() = !isSellOut && listOfNotNull(quantity, investedAmount).any { it > BigDecimal.ZERO }
}
