package online.mofish.tool.data.stock

import online.mofish.tool.data.http.MoFishHttpClient
import online.mofish.tool.domain.StockDailyKLine
import online.mofish.tool.domain.StockExchange
import online.mofish.tool.domain.StockQuote
import java.math.BigDecimal
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class StockKLineClient(
    private val httpClient: MoFishHttpClient = MoFishHttpClient(),
    private val kLineUrlProvider: (String, Int) -> String = ::defaultEastmoneyDailyKLineUrl,
    private val tencentKLineUrlProvider: (String, Int) -> String = ::defaultTencentDailyKLineUrl,
) {
    /**
     * 获取最近一段日 K 线数据。
     * @param quote 当前资产行情数据。
     * @param limit 最大 K 线数量。
     * @return 处理后的结果或当前状态。
     */
    fun fetchDailyKLines(quote: StockQuote, limit: Int = 24): List<StockDailyKLine> {
        val normalizedLimit = limit.coerceAtLeast(1)
        val requested = normalizeRequestedStock(quote.code)
        if (requested?.market == RequestedMarket.A) {
            val tencentKLines = runCatching {
                val payload = httpClient.getJson(
                    tencentKLineUrlProvider(requested.vendorCode, normalizedLimit)
                ).jsonObject
                parseTencentDailyKLines(payload, requested.vendorCode)
            }.getOrDefault(emptyList())
            if (tencentKLines.isNotEmpty()) {
                return tencentKLines.takeLast(normalizedLimit)
            }
        }

        val secId = eastmoneySecIdForQuote(quote) ?: return emptyList()
        val payload = httpClient.getJson(kLineUrlProvider(secId, normalizedLimit)).jsonObject
        val rows = payload["data"]
            ?.jsonObject
            ?.get("klines")
            ?.jsonArray
            ?: return emptyList()

        return rows.mapNotNull { row ->
            parseKLineRow(row.jsonPrimitive.content)
        }
    }
}

private fun defaultTencentDailyKLineUrl(vendorCode: String, limit: Int): String {
    return "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get" +
        "?param=$vendorCode,day,,,${limit.coerceAtLeast(1)},qfq"
}

internal fun eastmoneySecIdForQuote(quote: StockQuote): String? {
    val requested = normalizeRequestedStock(quote.code)
    val symbol = requested?.displaySymbol?.takeIf { it.isNotBlank() }
        ?: quote.symbol.trim().ifBlank { quote.code.filter(Char::isDigit) }
    if (symbol.isBlank()) {
        return null
    }
    val marketCode = when (requested?.exchange ?: quote.exchange) {
        StockExchange.SSE -> "1"
        StockExchange.SZSE,
        StockExchange.BSE,
        -> "0"
        StockExchange.HKEX -> "116"
        StockExchange.NASDAQ,
        StockExchange.NYSE,
        StockExchange.OTHER,
        -> "105"
    }
    return "$marketCode.${URLEncoder.encode(symbol, Charsets.UTF_8)}"
}

private fun defaultEastmoneyDailyKLineUrl(secId: String, limit: Int): String {
    return "https://push2his.eastmoney.com/api/qt/stock/kline/get" +
        "?secid=$secId&fields1=f1,f2,f3,f4,f5,f6" +
        "&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61" +
        "&klt=101&fqt=1&end=20500101&lmt=${limit.coerceAtLeast(1)}"
}

private fun parseKLineRow(row: String): StockDailyKLine? {
    val fields = row.split(',')
    if (fields.size < 6) {
        return null
    }
    return StockDailyKLine(
        date = runCatching { LocalDate.parse(fields[0], DATE_FORMATTER) }.getOrNull() ?: return null,
        open = fields[1].decimalValue() ?: return null,
        close = fields[2].decimalValue() ?: return null,
        high = fields[3].decimalValue() ?: return null,
        low = fields[4].decimalValue() ?: return null,
        volume = fields.getOrNull(5)?.decimalValue(),
    )
}

internal fun parseTencentDailyKLines(
    payload: JsonObject,
    vendorCode: String,
): List<StockDailyKLine> {
    val stockData = payload["data"]
        ?.jsonObject
        ?.get(vendorCode)
        ?.jsonObject
        ?: return emptyList()
    val rows = stockData["qfqday"]?.jsonArray
        ?: stockData["day"]?.jsonArray
        ?: return emptyList()

    return rows.mapNotNull { row ->
        val fields = row.jsonArray
        if (fields.size < 6) {
            return@mapNotNull null
        }
        StockDailyKLine(
            date = fields[0].jsonPrimitive.content.toDateValue() ?: return@mapNotNull null,
            open = fields[1].jsonPrimitive.content.decimalValue() ?: return@mapNotNull null,
            close = fields[2].jsonPrimitive.content.decimalValue() ?: return@mapNotNull null,
            high = fields[3].jsonPrimitive.content.decimalValue() ?: return@mapNotNull null,
            low = fields[4].jsonPrimitive.content.decimalValue() ?: return@mapNotNull null,
            volume = fields[5].jsonPrimitive.content.decimalValue(),
        )
    }
}

private fun String.decimalValue(): BigDecimal? {
    return trim()
        .takeIf { it.isNotEmpty() && it != "-" && it != "--" }
        ?.toBigDecimalOrNull()
}

private fun String.toDateValue(): LocalDate? {
    return runCatching { LocalDate.parse(this, DATE_FORMATTER) }.getOrNull()
}

private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
