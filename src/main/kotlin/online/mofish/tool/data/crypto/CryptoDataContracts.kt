package online.mofish.tool.data.crypto

import online.mofish.tool.domain.CryptoQuote
import online.mofish.tool.domain.CryptoSearchSuggestion

interface CryptoQuoteProvider {
    val providerName: String

    /**
     * 批量获取请求资产的最新行情。
     * @param codes codes。
     * @return 处理后的结果或当前状态。
     */
    fun fetchQuotes(codes: List<String>): Map<String, CryptoQuote>
}

interface CryptoSearchIndexProvider {
    val providerName: String

    /**
     * 加载Index数据。
     * @return 处理后的结果或当前状态。
     */
    fun loadIndex(): List<CryptoSearchSuggestion>
}
