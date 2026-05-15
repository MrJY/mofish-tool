package online.mofish.tool.data.crypto

import online.mofish.tool.data.http.MoFishHttpClient
import online.mofish.tool.domain.CryptoQuote
import online.mofish.tool.domain.CryptoSearchSuggestion

class CryptoQuoteClient(
    private val quoteProviders: List<CryptoQuoteProvider>,
    private val searchIndexProviders: List<CryptoSearchIndexProvider>,
) {
    constructor(
        httpClient: MoFishHttpClient = MoFishHttpClient(),
        vsCurrency: String = DEFAULT_VS_CURRENCY,
        marketsUrlProvider: (String, List<String>) -> String = ::defaultCoinGeckoMarketsUrl,
        listUrlProvider: () -> String = ::defaultCoinGeckoListUrl,
    ) : this(
        quoteProviders = listOf(
            CoinGeckoCryptoQuoteProvider(
                httpClient = httpClient,
                vsCurrency = vsCurrency,
                marketsUrlProvider = marketsUrlProvider,
            )
        ),
        searchIndexProviders = listOf(
            CachedCryptoSearchIndexProvider(
                CoinGeckoCryptoSearchIndexProvider(
                    httpClient = httpClient,
                    listUrlProvider = listUrlProvider,
                )
            )
        ),
    )

    fun fetchQuote(code: String): CryptoQuote {
        val normalizedCode = normalizeCryptoCode(code)
        require(normalizedCode.isNotEmpty()) { "虚拟币 ID 不能为空。" }

        return fetchQuotes(listOf(normalizedCode)).firstOrNull()
            ?: throw IllegalArgumentException("无法获取虚拟币数据：$normalizedCode")
    }

    fun fetchQuotes(codes: List<String>): List<CryptoQuote> {
        val normalizedCodes = codes
            .map(::normalizeCryptoCode)
            .filter { it.isNotEmpty() }
            .distinct()
        if (normalizedCodes.isEmpty()) {
            return emptyList()
        }

        quoteProviders.forEach { provider ->
            val quotesByCode = runCatching { provider.fetchQuotes(normalizedCodes) }.getOrDefault(emptyMap())
            if (quotesByCode.isNotEmpty()) {
                return normalizedCodes.mapNotNull(quotesByCode::get)
            }
        }

        return emptyList()
    }

    fun searchSuggestions(keyword: String): List<CryptoSearchSuggestion> {
        val normalizedKeyword = keyword.trim()
        require(normalizedKeyword.isNotEmpty()) { "搜索关键词不能为空。" }

        val searchToken = normalizedKeyword.lowercase()
        return loadSearchIndex()
            .asSequence()
            .filter { suggestion ->
                suggestion.code.contains(searchToken, ignoreCase = true) ||
                    suggestion.symbol.contains(searchToken, ignoreCase = true) ||
                    suggestion.name.contains(normalizedKeyword, ignoreCase = true)
            }
            .sortedWith(compareBy<CryptoSearchSuggestion> { cryptoSearchRank(it, searchToken) }.thenBy { it.name.lowercase() })
            .take(20)
            .toList()
    }

    private fun loadSearchIndex(): List<CryptoSearchSuggestion> {
        val merged = linkedMapOf<String, CryptoSearchSuggestion>()
        searchIndexProviders.forEach { provider ->
            val index = runCatching { provider.loadIndex() }.getOrDefault(emptyList())
            index.forEach { suggestion ->
                merged.putIfAbsent(suggestion.code, suggestion)
            }
        }
        return merged.values.toList()
    }

    private fun cryptoSearchRank(
        suggestion: CryptoSearchSuggestion,
        keyword: String,
    ): Int {
        val code = suggestion.code.lowercase()
        val symbol = suggestion.symbol.lowercase()
        val name = suggestion.name.lowercase()
        return when {
            code == keyword -> 0
            symbol == keyword -> 1
            code.startsWith(keyword) -> 2
            symbol.startsWith(keyword) -> 3
            name.startsWith(keyword) -> 4
            name.contains(keyword) -> 5
            else -> 6
        }
    }
}
