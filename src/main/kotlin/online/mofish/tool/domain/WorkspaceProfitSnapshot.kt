package online.mofish.tool.domain

import java.math.BigDecimal

data class PositionProfitSnapshot(
    /** 持仓配置 ID，用于从收益明细回溯到持仓。 */
    val holdingId: String,
    /** 当前收益明细所属资产类型。 */
    val assetType: AssetType,
    /** 当前收益明细对应的资产代码。 */
    val code: String,
    /** 当前收益明细展示名称。 */
    val displayName: String,
    /** 持仓数量或份额。 */
    val quantity: BigDecimal?,
    /** 持仓成本总额，已按需要转换到统计币种。 */
    val costAmount: BigDecimal?,
    /** 当前持仓市值，已按需要转换到统计币种。 */
    val currentValue: BigDecimal?,
    /** 累计盈亏金额。 */
    val totalProfit: BigDecimal?,
    /** 累计盈亏百分比。 */
    val totalProfitPercent: BigDecimal?,
    /** 今日盈亏金额。 */
    val todayProfit: BigDecimal?,
    /** 今日盈亏百分比。 */
    val todayProfitPercent: BigDecimal?,
    /** 当前资产是否成功匹配到可用行情。 */
    val quoteAvailable: Boolean,
)

data class AssetProfitSummary(
    /** 当前汇总所属资产类型。 */
    val assetType: AssetType,
    /** 该资产类型下所有持仓的成本总额。 */
    val totalCost: BigDecimal,
    /** 该资产类型下所有持仓的当前总市值。 */
    val currentValue: BigDecimal,
    /** 该资产类型下所有持仓的累计盈亏金额。 */
    val totalProfit: BigDecimal,
    /** 该资产类型下所有持仓的累计盈亏百分比。 */
    val totalProfitPercent: BigDecimal?,
    /** 该资产类型下所有持仓的今日盈亏金额。 */
    val todayProfit: BigDecimal,
    /** 该资产类型下所有持仓的今日盈亏百分比。 */
    val todayProfitPercent: BigDecimal?,
    /** 该资产类型下的逐持仓收益明细。 */
    val items: List<PositionProfitSnapshot>,
)

data class WorkspaceProfitSnapshot(
    /** 基金持仓收益汇总。 */
    val fundSummary: AssetProfitSummary,
    /** 股票持仓收益汇总。 */
    val stockSummary: AssetProfitSummary,
    /** 虚拟币持仓收益汇总。 */
    val cryptoSummary: AssetProfitSummary,
)
