package online.mofish.tool.domain

import java.math.BigDecimal

data class PositionProfitSnapshot(
    val holdingId: String,
    val assetType: AssetType,
    val code: String,
    val displayName: String,
    val quantity: BigDecimal?,
    val costAmount: BigDecimal?,
    val currentValue: BigDecimal?,
    val totalProfit: BigDecimal?,
    val totalProfitPercent: BigDecimal?,
    val todayProfit: BigDecimal?,
    val todayProfitPercent: BigDecimal?,
    val quoteAvailable: Boolean,
)

data class AssetProfitSummary(
    val assetType: AssetType,
    val totalCost: BigDecimal,
    val currentValue: BigDecimal,
    val totalProfit: BigDecimal,
    val totalProfitPercent: BigDecimal?,
    val todayProfit: BigDecimal,
    val todayProfitPercent: BigDecimal?,
    val items: List<PositionProfitSnapshot>,
)

data class WorkspaceProfitSnapshot(
    val fundSummary: AssetProfitSummary,
    val stockSummary: AssetProfitSummary,
    val cryptoSummary: AssetProfitSummary,
)
