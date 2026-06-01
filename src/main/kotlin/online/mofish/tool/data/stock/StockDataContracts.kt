package online.mofish.tool.data.stock

import online.mofish.tool.domain.StockQuote
import online.mofish.tool.domain.StockSearchSuggestion

internal interface StockQuoteProvider {
    val providerName: String

    /**
     * 批量获取请求资产的最新行情。
     * @param requestedStocks 需要批量查询行情的股票请求列表。
     * @return 处理后的结果或当前状态。
     */
    fun fetchQuotes(requestedStocks: List<RequestedStock>): Map<String, StockQuote>
}

internal interface AShareQuoteProvider : StockQuoteProvider

internal interface HongKongQuoteProvider : StockQuoteProvider

internal interface UsQuoteProvider : StockQuoteProvider

internal interface StockSearchSuggestionProvider {
    val providerName: String

    /**
     * 根据用户输入搜索可添加的候选资产。
     * @param keyword 用户输入的搜索关键字。
     * @return 处理后的结果或当前状态。
     */
    fun searchSuggestions(keyword: String): List<StockSearchSuggestion>
}
