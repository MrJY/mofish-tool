package online.mofish.tool.data.index

data class MarketIndexDefinition(
    val code: String,
    val displayName: String,
    val marketLabel: String,
)

internal fun defaultMarketIndexDefinitions(): List<MarketIndexDefinition> {
    return DEFAULT_MARKET_INDEX_DEFINITIONS
}

internal fun defaultMarketIndexCodes(): List<String> {
    return DEFAULT_MARKET_INDEX_DEFINITIONS.map { it.code }
}

internal fun marketIndexDefinitionFor(code: String): MarketIndexDefinition? {
    return DEFAULT_MARKET_INDEX_DEFINITIONS.firstOrNull { it.code.equals(code, ignoreCase = true) }
}

private val DEFAULT_MARKET_INDEX_DEFINITIONS = listOf(
    MarketIndexDefinition(code = "sh000001", displayName = "上证指数", marketLabel = "A股"),
    MarketIndexDefinition(code = "sz399001", displayName = "深证成指", marketLabel = "A股"),
    MarketIndexDefinition(code = "sz399006", displayName = "创业板指", marketLabel = "A股"),
    MarketIndexDefinition(code = "sh000300", displayName = "沪深300", marketLabel = "A股"),
    MarketIndexDefinition(code = "sh000688", displayName = "科创50", marketLabel = "A股"),
    MarketIndexDefinition(code = "sh000016", displayName = "上证50", marketLabel = "A股"),
    MarketIndexDefinition(code = "hkhsi", displayName = "恒生指数", marketLabel = "港股"),
    MarketIndexDefinition(code = "hkhscei", displayName = "恒生中国企业指数", marketLabel = "港股"),
    MarketIndexDefinition(code = "usixic", displayName = "纳斯达克综合指数", marketLabel = "美股"),
    MarketIndexDefinition(code = "usinx", displayName = "标普500指数", marketLabel = "美股"),
    MarketIndexDefinition(code = "usdji", displayName = "道琼斯工业平均指数", marketLabel = "美股"),
)
