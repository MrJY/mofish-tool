package online.mofish.tool.ui.toolwindow.modules

import online.mofish.tool.data.index.defaultMarketIndexDefinitions
import online.mofish.tool.domain.CryptoQuote
import online.mofish.tool.domain.ForexRate
import online.mofish.tool.domain.FundQuote
import online.mofish.tool.domain.HoldingConfig
import online.mofish.tool.domain.PositionProfitSnapshot
import online.mofish.tool.domain.StockQuote

internal const val ALL_STOCK_GROUP = "全部"
internal const val LIST_CARD = "list"
internal const val DETAIL_CARD = "detail"
internal const val CARD_LIST_CARD = "card"
internal const val TABLE_LIST_CARD = "table"
internal val INDEX_PRIORITY_CODES = defaultMarketIndexDefinitions().map { it.code.lowercase() }
internal val FOREX_PRIORITY_NAMES = listOf("美元", "港币", "欧元", "英镑", "日元", "澳门元", "新台币", "韩国元", "卢布")

internal enum class AssetListViewMode(val displayName: String, val cardId: String) {
    CARD("卡片视图", CARD_LIST_CARD),
    TABLE("表格视图", TABLE_LIST_CARD),
    ;

    /**
     * 处理 next 相关逻辑，并返回调用方需要的结果。
     * @return 处理后的结果或当前状态。
     */
    fun next(): AssetListViewMode {
        val values = entries
        return values[(values.indexOf(this) + 1) % values.size]
    }
}

internal enum class FundGroupFilter(val displayName: String) {
    ALL("全部"),
    HELD("持仓中"),
    WATCHLIST_ONLY("仅关注"),
    ;

    /**
     * 处理 next 相关逻辑，并返回调用方需要的结果。
     * @return 处理后的结果或当前状态。
     */
    fun next(): FundGroupFilter {
        val values = entries
        return values[(values.indexOf(this) + 1) % values.size]
    }
}

internal data class StockListItem(
    override val quote: StockQuote,
    val groupName: String?,
    val holding: HoldingConfig?,
    val profit: PositionProfitSnapshot?,
) : AssetRow<StockQuote> {
    override val code: String = quote.code
}

internal data class StockGroupTableValue(
    val groupName: String?,
) {
    /**
     * 转换为String表示。
     * @return 处理后的结果或当前状态。
     */
    override fun toString(): String = groupName?.takeIf { it.isNotBlank() } ?: "无分组"
}

internal data class IndexListItem(
    override val quote: StockQuote,
    val marketLabel: String,
) : AssetRow<StockQuote> {
    override val code: String = quote.code
}

internal data class FundListItem(
    override val quote: FundQuote,
    val holding: HoldingConfig?,
    val profit: PositionProfitSnapshot?,
) : AssetRow<FundQuote> {
    override val code: String = quote.code
}

internal data class CryptoListItem(
    override val quote: CryptoQuote,
    val holding: HoldingConfig?,
    val profit: PositionProfitSnapshot?,
) : AssetRow<CryptoQuote> {
    override val code: String = quote.code
}

internal data class ForexListItem(
    override val quote: ForexRate,
) : AssetRow<ForexRate> {
    override val code: String = quote.currencyCode
}

internal fun List<StockListItem>.containsStockCode(code: String?): Boolean {
    return !code.isNullOrBlank() && any { it.quote.code.equals(code, ignoreCase = true) }
}

internal fun List<IndexListItem>.containsIndexCode(code: String?): Boolean {
    return !code.isNullOrBlank() && any { it.quote.code.equals(code, ignoreCase = true) }
}

internal fun List<FundListItem>.containsFundCode(code: String?): Boolean {
    return !code.isNullOrBlank() && any { it.quote.code.equals(code, ignoreCase = true) }
}

internal fun List<CryptoListItem>.containsCryptoCode(code: String?): Boolean {
    return !code.isNullOrBlank() && any { it.quote.code.equals(code, ignoreCase = true) }
}

internal fun List<ForexListItem>.containsForexCode(code: String?): Boolean {
    return !code.isNullOrBlank() && any { it.quote.currencyCode.equals(code, ignoreCase = true) }
}
