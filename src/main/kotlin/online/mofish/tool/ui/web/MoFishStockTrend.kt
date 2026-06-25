package online.mofish.tool.ui.web

import online.mofish.tool.domain.StockExchange
import online.mofish.tool.domain.StockQuote

object MoFishStockTrend {
    /**
     * 处理 requestFor 相关逻辑，并返回调用方需要的结果。
     * @param quote 当前资产行情数据。
     * @return 处理后的结果或当前状态。
     */
    fun requestFor(quote: StockQuote): MoFishWebRequest {
        return MoFishWebRequest.Url(
            title = "mofish股票走势 - ${quote.name}",
            url = eastMoneyTrendUrl(quote),
        )
    }

    /**
     * 处理 eastMoneyTrendUrl 相关逻辑，并返回调用方需要的结果。
     * @param quote 当前资产行情数据。
     * @return 处理后的结果或当前状态。
     */
    private fun eastMoneyTrendUrl(quote: StockQuote): String {
        val secId = eastMoneySecId(quote)
        return if (secId != null) {
            "https://quote.eastmoney.com/basic/full.html?mcid=$secId"
        } else {
            sinaFallbackUrl(quote)
        }
    }

    /**
     * 处理 eastMoneySecId 相关逻辑，并返回调用方需要的结果。
     * @param quote 当前资产行情数据。
     * @return 处理后的结果或当前状态。
     */
    private fun eastMoneySecId(quote: StockQuote): String? {
        val symbol = quote.symbol.trim().lowercase()
        val codeDigits = quote.code.filter(Char::isDigit)
        return when (quote.exchange) {
            StockExchange.SSE -> codeDigits.ifBlank { symbol }.takeIf { it.isNotBlank() }?.let { "1.$it" }
            StockExchange.SZSE,
            StockExchange.BSE,
            -> codeDigits.ifBlank { symbol }.takeIf { it.isNotBlank() }?.let { "0.$it" }
            StockExchange.HKEX -> codeDigits.ifBlank { symbol }.takeIf { it.isNotBlank() }?.let { "116.$it" }
            StockExchange.NASDAQ,
            StockExchange.NYSE,
            StockExchange.OTHER,
            -> symbol.ifBlank { quote.code.removePrefix("us").lowercase() }.takeIf { it.isNotBlank() }?.let { "105.$it" }
        }
    }

    /**
     * 处理 sinaFallbackUrl 相关逻辑，并返回调用方需要的结果。
     * @param quote 当前资产行情数据。
     * @return 处理后的结果或当前状态。
     */
    private fun sinaFallbackUrl(quote: StockQuote): String {
        val normalized = quote.code.lowercase()
        return "https://finance.sina.com.cn/realstock/company/$normalized/nc.shtml"
    }
}
