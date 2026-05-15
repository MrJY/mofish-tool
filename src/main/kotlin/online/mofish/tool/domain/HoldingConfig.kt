package online.mofish.tool.domain

import java.math.BigDecimal

data class HoldingConfig(
    val id: String,
    val assetType: AssetType,
    val code: String,
    val displayName: String,
    val investedAmount: BigDecimal? = null,
    val quantity: BigDecimal? = null,
    val costPrice: BigDecimal,
    val todayCostPrice: BigDecimal? = null,
    val currency: String = "CNY",
    val isSellOut: Boolean = false,
) {
    val hasPosition: Boolean
        get() = !isSellOut && listOfNotNull(quantity, investedAmount).any { it > BigDecimal.ZERO }
}
