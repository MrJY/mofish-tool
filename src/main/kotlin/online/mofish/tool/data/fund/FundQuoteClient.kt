package online.mofish.tool.data.fund

import online.mofish.tool.data.http.MoFishHttpClient
import online.mofish.tool.domain.FundQuote
import online.mofish.tool.domain.FundSearchSuggestion

class FundQuoteClient(
    private val quoteProviders: List<FundQuoteProvider>,
    private val searchIndexProviders: List<FundSearchIndexProvider>,
) {
    constructor(
        httpClient: MoFishHttpClient = MoFishHttpClient(),
        quoteUrlProvider: (String) -> String = ::defaultFundQuoteUrl,
    ) : this(
        quoteProviders = listOf(
            EastMoneyFundQuoteProvider(
                httpClient = httpClient,
                quoteUrlProvider = quoteUrlProvider,
            )
        ),
        searchIndexProviders = listOf(
            CachedFundSearchIndexProvider(
                EastMoneyFundSearchIndexProvider(
                    httpClient = httpClient,
                    searchIndexUrlProvider = ::defaultFundSearchIndexUrl,
                )
            )
        ),
    )

    constructor(
        httpClient: MoFishHttpClient,
        searchIndexUrlProvider: () -> String,
        quoteUrlProvider: (String) -> String = ::defaultFundQuoteUrl,
    ) : this(
        quoteProviders = listOf(
            EastMoneyFundQuoteProvider(
                httpClient = httpClient,
                quoteUrlProvider = quoteUrlProvider,
            )
        ),
        searchIndexProviders = listOf(
            CachedFundSearchIndexProvider(
                EastMoneyFundSearchIndexProvider(
                    httpClient = httpClient,
                    searchIndexUrlProvider = searchIndexUrlProvider,
                )
            )
        ),
    )

    /**
     * 从远程或本地数据源获取行情数据。
     * @param code 资产代码或业务标识。
     * @return 处理后的结果或当前状态。
     */
    fun fetchQuote(code: String): FundQuote {
        val normalizedCode = code.trim()
        require(normalizedCode.isNotEmpty()) { "摸鱼基金代码不能为空。" }

        quoteProviders.forEach { provider ->
            val quote = runCatching { provider.fetchQuote(normalizedCode) }.getOrNull()
            if (quote != null) {
                return quote
            }
        }
        throw IllegalArgumentException("无法获取基金数据：$normalizedCode")
    }

    /**
     * 批量获取请求资产的最新行情。
     * @param codes codes。
     * @return 处理后的结果或当前状态。
     */
    fun fetchQuotes(codes: List<String>): List<FundQuote> {
        return codes
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .map(::fetchQuote)
    }

    /**
     * 根据用户输入搜索可添加的候选资产。
     * @param keyword 用户输入的搜索关键字。
     * @return 处理后的结果或当前状态。
     */
    fun searchSuggestions(keyword: String): List<FundSearchSuggestion> {
        val normalizedKeyword = keyword.trim()
        require(normalizedKeyword.isNotEmpty()) { "搜索关键词不能为空。" }

        val searchToken = normalizedKeyword.lowercase()
        return loadSearchIndex()
            .asSequence()
            .filter { suggestion ->
                suggestion.code.contains(searchToken, ignoreCase = true) ||
                    suggestion.name.contains(normalizedKeyword, ignoreCase = true) ||
                    suggestion.abbreviation?.contains(searchToken, ignoreCase = true) == true ||
                    suggestion.pinyin?.contains(searchToken, ignoreCase = true) == true
            }
            .take(20)
            .toList()
    }

    /**
     * 解析行情响应数据，并转换为项目内部可用的结构。
     * @param requestedCode requested代码。
     * @param payload payload。
     * @return 处理后的结果或当前状态。
     */
    internal fun parseQuoteResponse(
        requestedCode: String,
        payload: String,
    ): FundQuote {
        return parseFundQuotePayload(MoFishHttpClient(), requestedCode, payload)
    }

    /**
     * 解析搜索Index响应数据，并转换为项目内部可用的结构。
     * @param payload payload。
     * @return 处理后的结果或当前状态。
     */
    internal fun parseSearchIndexResponse(payload: String): List<FundSearchSuggestion> {
        return parseFundSearchIndexPayload(MoFishHttpClient(), payload)
    }

    /**
     * 加载搜索Index数据。
     * @return 处理后的结果或当前状态。
     */
    private fun loadSearchIndex(): List<FundSearchSuggestion> {
        val merged = linkedMapOf<String, FundSearchSuggestion>()
        searchIndexProviders.forEach { provider ->
            val index = runCatching { provider.loadIndex() }.getOrDefault(emptyList())
            index.forEach { suggestion ->
                merged.putIfAbsent(suggestion.code, suggestion)
            }
        }
        return merged.values.toList()
    }
}
