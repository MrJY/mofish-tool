package online.mofish.tool.ui.web

import online.mofish.tool.domain.StockExchange
import online.mofish.tool.domain.StockQuote

object MoFishStockTrend {
    fun requestFor(quote: StockQuote): MoFishWebRequest {
        return MoFishWebRequest.Url(
            title = "摸鱼股票走势 - ${quote.name}",
            url = eastMoneyTrendUrl(quote),
        )
    }

    private fun eastMoneyTrendUrl(quote: StockQuote): String {
        val secId = eastMoneySecId(quote)
        return if (secId != null) {
            "https://quote.eastmoney.com/basic/full.html?mcid=$secId"
        } else {
            sinaFallbackUrl(quote)
        }
    }

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

    private fun sinaFallbackUrl(quote: StockQuote): String {
        val normalized = quote.code.lowercase()
        return "https://finance.sina.com.cn/realstock/company/$normalized/nc.shtml"
    }
}
