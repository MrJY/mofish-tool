package online.mofish.tool.data.stock

import online.mofish.tool.domain.StockQuote
import online.mofish.tool.domain.StockSearchSuggestion

internal interface StockQuoteProvider {
    val providerName: String

    fun fetchQuotes(requestedStocks: List<RequestedStock>): Map<String, StockQuote>
}

internal interface AShareQuoteProvider : StockQuoteProvider

internal interface HongKongQuoteProvider : StockQuoteProvider

internal interface UsQuoteProvider : StockQuoteProvider

internal interface StockSearchSuggestionProvider {
    val providerName: String

    fun searchSuggestions(keyword: String): List<StockSearchSuggestion>
}
