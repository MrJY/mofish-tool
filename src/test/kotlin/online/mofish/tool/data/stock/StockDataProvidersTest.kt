package online.mofish.tool.data.stock

import online.mofish.tool.data.http.MoFishHttpClient
import online.mofish.tool.domain.StockExchange
import online.mofish.tool.domain.StockQuote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

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

    @Test
    fun `tencent intraday rows parse price average and minute volume`() {
        val payload = MoFishHttpClient.defaultJson()
            .parseToJsonElement(
                """
                {
                  "data": {
                    "sz002594": {
                      "data": {
                        "date": "20260623",
                        "data": [
                          "0930 86.91 2808 24404328.00",
                          "0931 87.55 17010 148081057.07"
                        ]
                      }
                    }
                  }
                }
                """.trimIndent()
            )
            .jsonObject

        val points = parseTencentIntradayPoints(payload, "sz002594")

        assertEquals(2, points.size)
        assertEquals(LocalDateTime.of(2026, 6, 23, 9, 30), points[0].time)
        assertEquals(BigDecimal("86.91"), points[0].price)
        assertEquals(BigDecimal("86.910000"), points[0].averagePrice)
        assertEquals(BigDecimal("2808"), points[0].volume)
        assertEquals(BigDecimal("14202"), points[1].volume)
    }

    @Test
    fun `tencent qfq daily rows parse by ticker`() {
        val payload = MoFishHttpClient.defaultJson()
            .parseToJsonElement(
                """
                {
                  "data": {
                    "sz002594": {
                      "qfqday": [
                        ["2026-06-22", "87.000", "87.590", "87.900", "84.600", "637242.000"],
                        ["2026-06-23", "86.91", "85.00", "88.32", "84.75", "466217"]
                      ]
                    }
                  }
                }
                """.trimIndent()
            )
            .jsonObject

        val kLines = parseTencentDailyKLines(payload, "sz002594")

        assertEquals(2, kLines.size)
        assertEquals(BigDecimal("86.91"), kLines[1].open)
        assertEquals(BigDecimal("85.00"), kLines[1].close)
        assertEquals(BigDecimal("88.32"), kLines[1].high)
        assertEquals(BigDecimal("84.75"), kLines[1].low)
        assertEquals(BigDecimal("466217"), kLines[1].volume)
    }

    @Test
    fun `eastmoney news rows supports current array payload`() {
        val payload = MoFishHttpClient.defaultJson()
            .parseToJsonElement("""{"result":{"cmsArticleWebOld":[{"title":"比亚迪新闻"}]}}""")
            .jsonObject

        val rows = eastmoneyNewsRows(payload)

        assertNotNull(rows)
        assertEquals(1, rows!!.size)
    }

    @Test
    fun `eastmoney news rows keeps old list payload compatibility`() {
        val payload = MoFishHttpClient.defaultJson()
            .parseToJsonElement("""{"result":{"cmsArticleWebOld":{"list":[{"title":"比亚迪新闻"}]}}}""")
            .jsonObject

        val rows = eastmoneyNewsRows(payload)

        assertNotNull(rows)
        assertEquals(1, rows!!.size)
    }
}
