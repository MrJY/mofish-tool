package online.mofish.tool.domain

data class StockSearchSuggestion(
    /** 可添加股票的标准化代码。 */
    val code: String,
    /** 股票名称。 */
    val name: String,
    /** 股票所属交易所。 */
    val exchange: StockExchange,
    /** 搜索结果中展示的市场标签，例如 A 股、港股、美股。 */
    val marketLabel: String,
    /** 搜索结果补充描述，没有额外信息时为空。 */
    val description: String? = null,
    /** 搜索接口返回的资产类别，例如 GP 股票、ZS 指数。 */
    val category: String = "",
)
