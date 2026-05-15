package online.mofish.tool.domain

import java.math.BigDecimal

data class ReminderTrigger(
    val ruleId: String,
    val assetType: AssetType,
    val code: String,
    val displayName: String,
    val metric: ReminderMetric,
    val direction: ReminderDirection,
    val threshold: BigDecimal,
    val previousValue: BigDecimal,
    val currentValue: BigDecimal,
    val title: String,
    val message: String,
)
