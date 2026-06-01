package online.mofish.tool.data.fund

import online.mofish.tool.domain.FundQuote
import online.mofish.tool.domain.FundSearchSuggestion

interface FundQuoteProvider {
    val providerName: String

    /**
     * 从远程或本地数据源获取行情数据。
     * @param code 资产代码或业务标识。
     * @return 处理后的结果或当前状态。
     */
    fun fetchQuote(code: String): FundQuote?
}

interface FundSearchIndexProvider {
    val providerName: String

    /**
     * 加载Index数据。
     * @return 处理后的结果或当前状态。
     */
    fun loadIndex(): List<FundSearchSuggestion>
}
