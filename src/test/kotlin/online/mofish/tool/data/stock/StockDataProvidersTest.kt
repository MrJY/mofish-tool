package online.mofish.tool.data.stock

import online.mofish.tool.domain.StockExchange
import online.mofish.tool.domain.StockQuote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

class StockDataProvidersTest {
    @Test
    fun `toSearchSuggestion keeps Kechuang board stock category`() {
        val suggestion = toSearchSuggestion(
            JsonArray(
                listOf(
                    JsonPrimitive("sh"),
                    JsonPrimitive("688256"),
                    JsonPrimitive("寒武纪"),
                    JsonPrimitive(""),
                    JsonPrimitive("GP-A-KCB"),
                )
            )
        )

        assertNotNull(suggestion)
        assertEquals("sh688256", suggestion!!.code)
        assertEquals("寒武纪", suggestion.name)
        assertEquals(StockExchange.SSE, suggestion.exchange)
        assertEquals("A股", suggestion.marketLabel)
    }

    @Test
    fun `eastmoney secid maps Shenzhen A share code`() {
        val quote = StockQuote(
            code = "sz002217",
            name = "合力泰",
            symbol = "002217",
            exchange = StockExchange.SZSE,
            currentPrice = null,
            previousClose = null,
            openPrice = null,
            highPrice = null,
            lowPrice = null,
            changeAmount = null,
            changePercent = null,
            volume = null,
            turnover = null,
            updatedAt = null,
        )

        assertEquals("0.002217", eastmoneySecIdForQuote(quote))
    }
}
