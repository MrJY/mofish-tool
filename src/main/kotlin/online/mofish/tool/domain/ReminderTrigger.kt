package online.mofish.tool.domain

import java.math.BigDecimal

data class ReminderTrigger(
    /** 触发的提醒规则 ID。 */
    val ruleId: String,
    /** 触发提醒的资产类型。 */
    val assetType: AssetType,
    /** 触发提醒的资产代码。 */
    val code: String,
    /** 触发提醒的资产展示名称。 */
    val displayName: String,
    /** 本次触发监控的指标。 */
    val metric: ReminderMetric,
    /** 本次触发命中的方向。 */
    val direction: ReminderDirection,
    /** 提醒规则设定的阈值。 */
    val threshold: BigDecimal,
    /** 上一次用于比较的指标值。 */
    val previousValue: BigDecimal,
    /** 当前命中提醒的指标值。 */
    val currentValue: BigDecimal,
    /** 通知标题。 */
    val title: String,
    /** 通知正文消息。 */
    val message: String,
)
