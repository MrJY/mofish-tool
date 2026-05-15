package online.mofish.tool.domain

import java.math.BigDecimal

data class ReminderRule(
    val id: String,
    val assetType: AssetType,
    val code: String,
    val displayName: String,
    val metric: ReminderMetric,
    val direction: ReminderDirection,
    val threshold: BigDecimal,
    val enabled: Boolean = true,
)
