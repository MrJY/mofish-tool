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

internal fun escape(value: String): String {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}

internal fun marketColor(value: BigDecimal?): Color {
    return when {
        value == null -> JBColor.foreground()
        value.compareTo(BigDecimal.ZERO) > 0 -> RISE_COLOR
        value.compareTo(BigDecimal.ZERO) < 0 -> FALL_COLOR
        else -> JBColor.foreground()
    }
}

internal fun formatDecimal(value: BigDecimal?): String = value?.toPlainString() ?: "--"

internal fun formatDateTime(value: LocalDateTime?): String {
    return value?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) ?: "--"
}

internal fun formatInstant(value: Instant?): String {
    return value
        ?.let {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault())
                .format(it)
        }
        ?: "--"
}

internal fun buildDataUpdateSummary(
    snapshot: MoFishWatchlistState,
    module: MoFishRefreshModule,
): String {
    val refreshedAt = snapshot.projectState.moduleRefreshAt[module] ?: snapshot.projectState.lastRefreshAt
    return "数据更新时间：${formatInstant(refreshedAt)}"
}

internal fun formatPercent(value: BigDecimal?): String {
    return value?.toPlainString()?.let { "$it%" } ?: "--"
}

internal fun formatTenThousand(value: BigDecimal?): String {
    return value
        ?.divide(BigDecimal("10000"), 2, RoundingMode.HALF_UP)
        ?.stripTrailingZeros()
        ?.toPlainString()
        ?.let { "${it}万" }
        ?: "--"
}

internal fun colorHex(color: Color): String = "#%02x%02x%02x".format(color.red, color.green, color.blue)

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
        "排序 ${sortSettings.quoteField} / ${sortSettings.quoteDirection}",
    ).joinToString(" | ")
}
