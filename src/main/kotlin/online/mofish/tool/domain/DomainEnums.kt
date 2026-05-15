package online.mofish.tool.domain

enum class AssetType(
    private val displayName: String,
) {
    FUND("基金"),
    STOCK("股票"),
    FOREX("外汇"),
    CRYPTO("加密货币"),
    ;

    override fun toString(): String = displayName
}

enum class QuoteStatus {
    TRADING,
    CLOSED,
    HALTED,
    DELAYED,
    UNAVAILABLE,
}

enum class StockExchange {
    SSE,
    SZSE,
    BSE,
    HKEX,
    NASDAQ,
    NYSE,
    OTHER,
}

enum class ReminderMetric(
    private val displayName: String,
) {
    PRICE("价格"),
    CHANGE_PERCENT("涨跌幅"),
    ;

    override fun toString(): String = displayName
}

enum class ReminderDirection(
    private val displayName: String,
) {
    ABOVE("高于"),
    BELOW("低于"),
    ;

    override fun toString(): String = displayName
}

enum class AiStockHistoryRange(
    val wireValue: String,
    val label: String,
) {
    ONE_YEAR("1y", "1年"),
    SIX_MONTHS("6m", "6个月"),
    THREE_MONTHS("3m", "3个月"),
    ONE_MONTH("1m", "1个月"),
    ONE_WEEK("1w", "1周"),
    ;

    companion object {
        fun fromWireValue(value: String): AiStockHistoryRange {
            return entries.firstOrNull { it.wireValue == value } ?: THREE_MONTHS
        }
    }

    override fun toString(): String = label
}

enum class FlashNewsSource {
    JIN10,
    XUANGUBAO,
    CUSTOM,
}

enum class FlashNewsImpact {
    POSITIVE,
    NEUTRAL,
    NEGATIVE,
}
