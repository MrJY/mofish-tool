package online.mofish.tool.data.stock

import online.mofish.tool.data.http.MoFishHttpClient
import online.mofish.tool.domain.StockIntradayPoint
import online.mofish.tool.domain.StockQuote
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class StockIntradayClient(
    private val httpClient: MoFishHttpClient = MoFishHttpClient(),
    private val intradayUrlProvider: (String) -> String = ::defaultEastmoneyIntradayUrl,
) {
    /**
     * 获取当日分时点位。
     * @param quote 当前资产行情数据。
     * @return 处理后的结果或当前状态。
     */
    fun fetchIntradayPoints(quote: StockQuote): List<StockIntradayPoint> {
        val secId = eastmoneySecIdForQuote(quote) ?: return emptyList()
        val payload = httpClient.getJson(intradayUrlProvider(secId)).jsonObject
        val rows = payload["data"]
            ?.jsonObject
            ?.get("trends")
            ?.jsonArray
            ?: return emptyList()

        return rows.mapNotNull { row ->
            parseIntradayRow(row.jsonPrimitive.content)
        }
    }
}

private fun defaultEastmoneyIntradayUrl(secId: String): String {
    return "https://push2.eastmoney.com/api/qt/stock/trends2/get" +
        "?secid=$secId&fields1=f1,f2,f3,f4,f5,f6,f7,f8,f9,f10,f11" +
        "&fields2=f51,f52,f53,f54,f55,f56,f57,f58&iscr=0&iscca=0&ndays=1"
}

private fun parseIntradayRow(row: String): StockIntradayPoint? {
    val fields = row.split(',')
    if (fields.size < 2) {
        return null
    }
    return StockIntradayPoint(
        time = runCatching { LocalDateTime.parse(fields[0], INTRADAY_TIME_FORMATTER) }.getOrNull()
            ?: return null,
        price = fields[1].decimalValue() ?: return null,
        averagePrice = fields.getOrNull(2)?.decimalValue(),
        volume = fields.getOrNull(3)?.decimalValue(),
    )
}

private fun String.decimalValue(): BigDecimal? {
    return trim()
        .takeIf { it.isNotEmpty() && it != "-" && it != "--" }
        ?.toBigDecimalOrNull()
}

private val INTRADAY_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
