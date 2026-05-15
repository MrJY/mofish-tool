package online.mofish.tool.data.crypto

import online.mofish.tool.domain.CryptoQuote
import online.mofish.tool.domain.CryptoSearchSuggestion

interface CryptoQuoteProvider {
    val providerName: String

    fun fetchQuotes(codes: List<String>): Map<String, CryptoQuote>
}

interface CryptoSearchIndexProvider {
    val providerName: String

    fun loadIndex(): List<CryptoSearchSuggestion>
}
