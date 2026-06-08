package online.mofish.tool.data.index

data class MarketIndexDefinition(
    val code: String,
    val displayName: String,
    val marketLabel: String,
)

/**
 * 处理 defaultMarketIndexDefinitions 相关逻辑，并返回调用方需要的结果。
 * @return 处理后的结果或当前状态。
 */
internal fun defaultMarketIndexDefinitions(): List<MarketIndexDefinition> {
    return DEFAULT_MARKET_INDEX_DEFINITIONS
}

/**
 * 处理 defaultMarketIndexCodes 相关逻辑，并返回调用方需要的结果。
 * @return 处理后的结果或当前状态。
 */
internal fun defaultMarketIndexCodes(): List<String> {
    return DEFAULT_MARKET_INDEX_DEFINITIONS.map { it.code }
}

/**
 * 处理 marketIndexDefinitionFor 相关逻辑，并返回调用方需要的结果。
 * @param code 资产代码或业务标识。
 * @return 处理后的结果或当前状态。
 */
internal fun marketIndexDefinitionFor(code: String): MarketIndexDefinition? {
    return DEFAULT_MARKET_INDEX_DEFINITIONS.firstOrNull { it.code.equals(code, ignoreCase = true) }
}

private val DEFAULT_MARKET_INDEX_DEFINITIONS = listOf(
    MarketIndexDefinition(code = "sh000001", displayName = "上证指数", marketLabel = "A股"),
    MarketIndexDefinition(code = "sz399001", displayName = "深证成指", marketLabel = "A股"),
)
