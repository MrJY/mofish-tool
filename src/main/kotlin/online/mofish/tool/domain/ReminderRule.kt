package online.mofish.tool.domain

import java.math.BigDecimal

data class ReminderRule(
    /** 提醒规则唯一标识。 */
    val id: String,
    /** 提醒关联的资产类型。 */
    val assetType: AssetType,
    /** 提醒关联的资产代码。 */
    val code: String,
    /** 提醒在界面中展示的资产名称。 */
    val displayName: String,
    /** 提醒监控的指标，例如价格或涨跌幅。 */
    val metric: ReminderMetric,
    /** 提醒触发方向，例如高于或低于阈值。 */
    val direction: ReminderDirection,
    /** 提醒触发阈值。 */
    val threshold: BigDecimal,
    /** 提醒规则是否启用。 */
    val enabled: Boolean = true,
)
