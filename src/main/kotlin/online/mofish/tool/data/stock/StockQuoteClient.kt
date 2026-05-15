package online.mofish.tool.data.stock

import online.mofish.tool.data.http.MoFishHttpClient
import online.mofish.tool.domain.StockQuote
import online.mofish.tool.domain.StockSearchSuggestion

class StockQuoteClient private constructor(
    private val aShareQuoteProviders: List<AShareQuoteProvider>,
    private val hongKongQuoteProviders: List<HongKongQuoteProvider>,
    private val usQuoteProviders: List<UsQuoteProvider>,
    private val searchProviders: List<StockSearchSuggestionProvider>,
) {
    constructor(
        httpClient: MoFishHttpClient = MoFishHttpClient(),
        sinaQuoteUrlProvider: (List<String>) -> String = ::defaultSinaQuoteUrl,
        hongKongQuoteUrlProvider: (List<String>) -> String = ::defaultHongKongQuoteUrl,
        searchUrlProvider: (String) -> String = ::defaultStockSearchUrl,
        tencentAShareQuoteUrlProvider: (List<String>) -> String = ::defaultTencentAShareQuoteUrl,
    ) : this(
        aShareQuoteProviders = listOf(
            TencentAShareQuoteProvider(
                httpClient = httpClient,
                quoteUrlProvider = tencentAShareQuoteUrlProvider,
            ),
            SinaAShareQuoteProvider(
                httpClient = httpClient,
                quoteUrlProvider = sinaQuoteUrlProvider,
            ),
        ),
        hongKongQuoteProviders = listOf(
            TencentHongKongQuoteProvider(
                httpClient = httpClient,
                quoteUrlProvider = hongKongQuoteUrlProvider,
            )
        ),
        usQuoteProviders = listOf(
            SinaUsQuoteProvider(
                httpClient = httpClient,
                quoteUrlProvider = sinaQuoteUrlProvider,
            )
        ),
        searchProviders = listOf(
            TencentStockSearchSuggestionProvider(
                httpClient = httpClient,
                searchUrlProvider = searchUrlProvider,
            )
        ),
    )

    fun fetchQuote(code: String): StockQuote {
        val normalizedCode = code.trim()
        require(normalizedCode.isNotEmpty()) { "股票代码不能为空。" }

        return fetchQuotes(listOf(normalizedCode)).firstOrNull()
            ?: throw IllegalArgumentException("不支持的股票代码：$normalizedCode")
    }

    fun fetchQuotes(codes: List<String>): List<StockQuote> {
        val requestedStocks = codes
            .mapNotNull(::normalizeRequestedStock)
            .distinctBy { it.originalCode }
        if (requestedStocks.isEmpty()) {
            return emptyList()
        }

        val quotesByCode = linkedMapOf<String, StockQuote>()
        quotesByCode.putAll(
            fetchWithFallback(
                requestedStocks = requestedStocks.filter { it.market == RequestedMarket.A },
                providers = aShareQuoteProviders,
            )
        )
        quotesByCode.putAll(
            fetchWithFallback(
                requestedStocks = requestedStocks.filter { it.market == RequestedMarket.HK },
                providers = hongKongQuoteProviders,
            )
        )
        quotesByCode.putAll(
            fetchWithFallback(
                requestedStocks = requestedStocks.filter { it.market == RequestedMarket.US },
                providers = usQuoteProviders,
            )
        )

        return requestedStocks.map { requested ->
            quotesByCode[requested.originalCode] ?: unavailableQuote(requested)
        }
    }

    fun searchSuggestions(keyword: String): List<StockSearchSuggestion> {
        val normalizedKeyword = keyword.trim()
        require(normalizedKeyword.isNotEmpty()) { "搜索关键词不能为空。" }

        return searchProviders
            .asSequence()
            .flatMap { provider ->
                runCatching { provider.searchSuggestions(normalizedKeyword) }
                    .getOrDefault(emptyList())
                    .asSequence()
            }
            .distinctBy { it.code.lowercase() }
            .take(20)
            .toList()
    }
}

internal fun canonicalizeStockInputCode(rawCode: String): String? {
    val normalizedCode = rawCode.trim().lowercase()
    if (normalizedCode.isBlank()) {
        return null
    }

    return when {
        normalizedCode.startsWith("sh") ||
            normalizedCode.startsWith("sz") ||
            normalizedCode.startsWith("bj") ||
            normalizedCode.startsWith("hk") ||
            normalizedCode.startsWith("us") ||
            normalizedCode.startsWith("gb_") ||
            normalizedCode.startsWith("usr_") -> normalizedCode

        normalizedCode.matches(Regex("""\d{6}""")) -> inferChinaMarketPrefix(normalizedCode)?.plus(normalizedCode)
        else -> null
    }
}

internal fun inferChinaMarketPrefix(code: String): String? {
    val normalizedCode = code.trim()
    if (!normalizedCode.matches(Regex("""\d{6}"""))) {
        return null
    }

    return when {
        normalizedCode.startsWith("43") ||
            normalizedCode.startsWith("83") ||
            normalizedCode.startsWith("87") ||
            normalizedCode.startsWith("88") ||
            normalizedCode.startsWith("92") -> "bj"

        normalizedCode.startsWith("5") ||
            normalizedCode.startsWith("6") ||
            normalizedCode.startsWith("9") -> "sh"

        normalizedCode.startsWith("0") ||
            normalizedCode.startsWith("1") ||
            normalizedCode.startsWith("2") ||
            normalizedCode.startsWith("3") -> "sz"

        else -> null
    }
}

private fun fetchWithFallback(
    requestedStocks: List<RequestedStock>,
    providers: List<StockQuoteProvider>,
): Map<String, StockQuote> {
    if (requestedStocks.isEmpty()) {
        return emptyMap()
    }

    val resolved = linkedMapOf<String, StockQuote>()
    var remaining = requestedStocks

    providers.forEach { provider ->
        if (remaining.isEmpty()) {
            return@forEach
        }

        val quotes = runCatching { provider.fetchQuotes(remaining) }.getOrDefault(emptyMap())
        quotes.forEach { (code, quote) ->
            if (quote.status != online.mofish.tool.domain.QuoteStatus.UNAVAILABLE) {
                resolved.putIfAbsent(code, quote)
            }
        }
        remaining = remaining.filterNot { resolved.containsKey(it.originalCode) }
    }

    return resolved
}
