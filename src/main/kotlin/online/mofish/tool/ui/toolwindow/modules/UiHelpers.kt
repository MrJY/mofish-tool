package online.mofish.tool.ui.toolwindow.modules

import com.intellij.ui.JBColor
import online.mofish.tool.domain.MoFishRefreshModule
import online.mofish.tool.settings.MoFishSortSettings
import online.mofish.tool.state.MoFishWatchlistState
import java.awt.Color
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal val RISE_COLOR = JBColor(Color(0xD4380D), Color(0xFF7875))
internal val FALL_COLOR = JBColor(Color(0x389E0D), Color(0x73D13D))

/**
 * 转义 HTML 特殊字符，避免动态文本破坏页面结构。
 * @param value 待解析、格式化或写入的原始值。
 * @return 处理后的结果或当前状态。
 */
internal fun escape(value: String): String {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}

/**
 * 根据涨跌方向返回行情展示颜色。
 * @param value 待解析、格式化或写入的原始值。
 * @return 处理后的结果或当前状态。
 */
internal fun marketColor(value: BigDecimal?): Color {
    return when {
        value == null -> JBColor.foreground()
        value.compareTo(BigDecimal.ZERO) > 0 -> RISE_COLOR
        value.compareTo(BigDecimal.ZERO) < 0 -> FALL_COLOR
        else -> JBColor.foreground()
    }
}

/**
 * 把小数值格式化为界面展示文本，空值显示占位符。
 * @param value 待解析、格式化或写入的原始值。
 * @return 处理后的结果或当前状态。
 */
internal fun formatDecimal(value: BigDecimal?): String = value?.toPlainString() ?: "--"

/**
 * 把本地日期时间格式化为界面展示文本。
 * @param value 待解析、格式化或写入的原始值。
 * @return 处理后的结果或当前状态。
 */
internal fun formatDateTime(value: LocalDateTime?): String {
    return value?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) ?: "--"
}

/**
 * 把时间戳按系统时区格式化为界面展示文本。
 * @param value 待解析、格式化或写入的原始值。
 * @return 处理后的结果或当前状态。
 */
internal fun formatInstant(value: Instant?): String {
    return value
        ?.let {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault())
                .format(it)
        }
        ?: "--"
}

/**
 * 构建数据Update汇总，供后续界面展示或数据处理使用。
 * @param snapshot 当前状态或数据快照。
 * @param module 模块。
 * @return 处理后的结果或当前状态。
 */
internal fun buildDataUpdateSummary(
    snapshot: MoFishWatchlistState,
    module: MoFishRefreshModule,
): String {
    val refreshedAt = snapshot.projectState.moduleRefreshAt[module] ?: snapshot.projectState.lastRefreshAt
    return "数据更新时间：${formatInstant(refreshedAt)}"
}

/**
 * 把百分比数值格式化为界面展示文本。
 * @param value 待解析、格式化或写入的原始值。
 * @return 处理后的结果或当前状态。
 */
internal fun formatPercent(value: BigDecimal?): String {
    return value?.toPlainString()?.let { "$it%" } ?: "--"
}

/**
 * 格式化TenThousand，用于界面展示。
 * @param value 待解析、格式化或写入的原始值。
 * @return 处理后的结果或当前状态。
 */
internal fun formatTenThousand(value: BigDecimal?): String {
    return value
        ?.divide(BigDecimal("10000"), 2, RoundingMode.HALF_UP)
        ?.stripTrailingZeros()
        ?.toPlainString()
        ?.let { "${it}万" }
        ?: "--"
}

/**
 * 处理 colorHex 相关逻辑，并返回调用方需要的结果。
 * @param color 颜色。
 * @return 处理后的结果或当前状态。
 */
internal fun colorHex(color: Color): String = "#%02x%02x%02x".format(color.red, color.green, color.blue)

/**
 * 拼装资产模块顶部使用的汇总描述文本。
 * @param countText count文本。
 * @param profitText 收益文本。
 * @param extraText extra文本。
 * @param sortSettings sort设置。
 * @return 处理后的结果或当前状态。
 */
internal fun buildAssetSummary(
    countText: String,
    profitText: String? = null,
    extraText: String? = null,
    sortSettings: MoFishSortSettings,
): String {
    return listOfNotNull(
        countText,
        profitText,
        extraText,
        "排序 日涨跌幅 / ${sortSettings.quoteDirection}",
    ).joinToString(" | ")
}
