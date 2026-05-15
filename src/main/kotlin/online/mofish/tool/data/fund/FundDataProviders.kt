package online.mofish.tool.data.fund

import online.mofish.tool.data.http.MoFishHttpClient
import online.mofish.tool.domain.FundQuote
import online.mofish.tool.domain.FundSearchSuggestion
import online.mofish.tool.domain.QuoteStatus
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class EastMoneyFundQuoteProvider(
    private val httpClient: MoFishHttpClient = MoFishHttpClient(),
    private val quoteUrlProvider: (String) -> String = ::defaultFundQuoteUrl,
) : FundQuoteProvider {
    override val providerName: String = "eastmoney-fund-quote"

    override fun fetchQuote(code: String): FundQuote? {
        val normalizedCode = code.trim()
        if (normalizedCode.isEmpty()) {
            return null
        }
        val response = httpClient.get(quoteUrlProvider(normalizedCode))
        return parseFundQuotePayload(httpClient, normalizedCode, response.body)
    }
}

class EastMoneyFundSearchIndexProvider(
    private val httpClient: MoFishHttpClient = MoFishHttpClient(),
    private val searchIndexUrlProvider: () -> String = ::defaultFundSearchIndexUrl,
) : FundSearchIndexProvider {
    override val providerName: String = "eastmoney-fund-search-index"

    override fun loadIndex(): List<FundSearchSuggestion> {
        val payload = httpClient.get(searchIndexUrlProvider()).body
        return parseFundSearchIndexPayload(httpClient, payload)
    }
}

class CachedFundSearchIndexProvider(
    private val delegate: FundSearchIndexProvider,
) : FundSearchIndexProvider {
    override val providerName: String = "cached:${delegate.providerName}"

    @Volatile
    private var cache: List<FundSearchSuggestion>? = null

    override fun loadIndex(): List<FundSearchSuggestion> {
        val cached = cache
        if (cached != null) {
            return cached
        }

        return synchronized(this) {
            val secondRead = cache
            secondRead ?: delegate.loadIndex().also { cache = it }
        }
    }
}

internal fun parseFundQuotePayload(
    httpClient: MoFishHttpClient,
    requestedCode: String,
    payload: String,
): FundQuote {
    val jsonBody = FUND_JSONP_WRAPPER.find(payload.trim())?.groupValues?.get(1)
        ?: throw IllegalArgumentException("基金估值响应格式无效：$requestedCode")
    val data = httpClient.parseJson(jsonBody).jsonObject
    return data.toFundQuote(requestedCode)
}

internal fun parseFundSearchIndexPayload(
    httpClient: MoFishHttpClient,
    payload: String,
): List<FundSearchSuggestion> {
    val normalizedPayload = payload.removePrefix("﻿").trim()
    val jsonBody = when {
        normalizedPayload.startsWith("var r =") -> normalizedPayload.substringAfter("var r =")
        normalizedPayload.startsWith("var r=") -> normalizedPayload.substringAfter("var r=")
        else -> normalizedPayload
    }.trim().removeSuffix(";")

    return httpClient.parseJson(jsonBody)
        .jsonArray
        .mapNotNull(::toFundSearchSuggestion)
}

private fun JsonObject.toFundQuote(requestedCode: String): FundQuote {
    val code = stringValue("fundcode") ?: requestedCode
    val name = stringValue("name") ?: "$code 暂无数据"
    val estimatedNetValue = decimalValue("gsz")
    val previousNetValue = decimalValue("dwjz")
    val dailyChangePercent = decimalValue("gszzl")
    val valuationTime = dateTimeValue("gztime")
    val netValueDate = dateValue("jzrq")
    val status = when {
        estimatedNetValue == null -> QuoteStatus.UNAVAILABLE
        valuationTime != null && netValueDate != null && valuationTime.toLocalDate().isEqual(netValueDate) -> QuoteStatus.CLOSED
        else -> QuoteStatus.TRADING
    }

    return FundQuote(
        code = code,
        name = name,
        estimatedNetValue = estimatedNetValue,
        previousNetValue = previousNetValue,
        dailyChangePercent = dailyChangePercent,
        valuationTime = valuationTime,
        netValueDate = netValueDate,
        status = status,
        isEstimated = estimatedNetValue != null && valuationTime != null,
    )
}

private fun JsonObject.stringValue(key: String): String? {
    return this[key]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() }
}

private fun JsonObject.decimalValue(key: String): BigDecimal? {
    val raw = stringValue(key)
    if (raw == null || raw == "--") {
        return null
    }
    return raw.toBigDecimalOrNull()
}

private fun JsonObject.dateTimeValue(key: String): LocalDateTime? {
    val raw = stringValue(key) ?: return null
    return runCatching { LocalDateTime.parse(raw.replace(" ", "T")) }.getOrNull()
}

private fun JsonObject.dateValue(key: String): LocalDate? {
    val raw = stringValue(key) ?: return null
    return runCatching { LocalDate.parse(raw) }.getOrNull()
}

private fun toFundSearchSuggestion(item: JsonElement): FundSearchSuggestion? {
    val fields = item.jsonArray
    val code = fields.stringValue(0) ?: return null
    val name = fields.stringValue(2) ?: return null
    return FundSearchSuggestion(
        code = code,
        abbreviation = fields.stringValue(1),
        name = name,
        fundType = fields.stringValue(3) ?: "基金",
        pinyin = fields.stringValue(4),
    )
}

private fun JsonArray.stringValue(index: Int): String? {
    return getOrNull(index)?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() }
}

private val FUND_JSONP_WRAPPER = Regex("""jsonpgz\(([\s\S]*)\);?""")

internal fun defaultFundQuoteUrl(code: String): String = "https://fundgz.1234567.com.cn/js/${code.trim()}.js"

internal fun defaultFundSearchIndexUrl(): String = "https://fund.eastmoney.com/js/fundcode_search.js"
