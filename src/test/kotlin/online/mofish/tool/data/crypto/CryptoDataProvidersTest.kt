package online.mofish.tool.data.crypto

import online.mofish.tool.data.http.MoFishHttpClient
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CryptoDataProvidersTest {
    @Test
    fun `parseBinanceTickerQuote maps ethereum ticker`() {
        val payload = MoFishHttpClient().parseJson(
            """
            {
              "symbol": "ETHUSDT",
              "lastPrice": "1667.33000000",
              "quoteVolume": "770220334.74431200",
              "priceChangePercent": "3.048",
              "closeTime": 1780907425000
            }
            """.trimIndent()
        ).jsonObject

        val quote = parseBinanceTickerQuote(
            code = "ethereum",
            tickerSymbol = "ETHUSDT",
            payload = payload,
        )

        assertNotNull(quote)
        assertEquals("ethereum", quote!!.code)
        assertEquals("ETH", quote.symbol)
        assertEquals("Ethereum", quote.name)
        assertEquals("1667.33000000".toBigDecimal(), quote.currentPrice)
        assertEquals("3.048".toBigDecimal(), quote.priceChangePercentage24h)
        assertEquals("USD", quote.quoteCurrency)
    }
}
