package online.mofish.tool.data.fund

import online.mofish.tool.domain.FundQuote
import online.mofish.tool.domain.FundSearchSuggestion

interface FundQuoteProvider {
    val providerName: String

    fun fetchQuote(code: String): FundQuote?
}

interface FundSearchIndexProvider {
    val providerName: String

    fun loadIndex(): List<FundSearchSuggestion>
}
