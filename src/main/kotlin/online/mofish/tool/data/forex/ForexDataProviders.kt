package online.mofish.tool.data.forex

import online.mofish.tool.data.http.MoFishHttpClient
import online.mofish.tool.domain.ForexRate
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class BocForexRateProvider(
    private val httpClient: MoFishHttpClient = MoFishHttpClient(),
    private val ratesUrlProvider: () -> String = ::defaultBocForexRatesUrl,
) : ForexRateProvider {
    override val providerName: String = "boc-forex-rates"

    /**
     * 获取外汇牌价列表。
     * @return 处理后的结果或当前状态。
     */
    override fun fetchRates(): List<ForexRate> {
        val document = httpClient.getHtml(
            ratesUrlProvider(),
            headers = mapOf("Referer" to "https://www.boc.cn/"),
        )
        return parseBocForexDocument(document)
    }
}

/**
 * 解析Boc外汇Document数据，并转换为项目内部可用的结构。
 * @param document document。
 * @return 处理后的结果或当前状态。
 */
internal fun parseBocForexDocument(document: Document): List<ForexRate> {
    return document.select("#priceTable tr")
        .mapNotNull(::toForexRate)
}

/**
 * 转换为外汇汇率表示。
 * @param row 待添加、转换或展示的行数据。
 * @return 处理后的结果或当前状态。
 */
private fun toForexRate(row: Element): ForexRate? {
    val cells = row.select("td")
    if (cells.size < 8) {
        return null
    }

    val currencyName = cells[0].normalizedCellText().ifBlank {
        row.attr("data-currency").trim()
    }
    if (currencyName.isBlank()) {
        return null
    }

    return ForexRate(
        currencyName = currencyName,
        currencyCode = buildForexCurrencyPairCode(currencyName),
        spotBuyPrice = cells[1].decimalValue(),
        cashBuyPrice = cells[2].decimalValue(),
        spotSellPrice = cells[3].decimalValue(),
        cashSellPrice = cells[4].decimalValue(),
        conversionPrice = cells[5].decimalValue(),
        publishedAt = parseBocPublishedAt(
            dateText = cells[6].normalizedCellText(),
            timeText = cells[7].normalizedCellText(),
        ),
    )
}

/**
 * 构建外汇币种Pair代码，供后续界面展示或数据处理使用。
 * @param rawCurrency 用户输入或接口返回的原始币种文本。
 * @return 处理后的结果或当前状态。
 */
internal fun buildForexCurrencyPairCode(rawCurrency: String): String {
    val baseCurrency = normalizeForexBaseCurrency(rawCurrency) ?: rawCurrency.trim().ifBlank { "UNKNOWN" }
    return "$baseCurrency/CNY"
}

/**
 * 规范化外汇Base币种，统一后续处理使用的表示形式。
 * @param rawCurrency 用户输入或接口返回的原始币种文本。
 * @return 处理后的结果或当前状态。
 */
internal fun normalizeForexBaseCurrency(rawCurrency: String): String? {
    val normalized = rawCurrency.trim()
    if (normalized.isBlank()) {
        return null
    }

    val baseCandidate = normalized
        .substringBefore("/")
        .substringBefore("-")
        .trim()
    val upperCandidate = baseCandidate.uppercase()

    return FOREX_CURRENCY_ALIASES[normalized]
        ?: FOREX_CURRENCY_ALIASES[baseCandidate]
        ?: FOREX_CURRENCY_ALIASES[upperCandidate]
        ?: upperCandidate.takeIf { it.matches(ISO_CURRENCY_CODE_PATTERN) }
}

private fun Element.decimalValue(): BigDecimal? {
    val raw = normalizedCellText()
    if (raw.isBlank() || raw == "--") {
        return null
    }
    return raw.replace(",", "").toBigDecimalOrNull()
}

private fun Element.normalizedCellText(): String {
    return text()
        .replace(' ', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
}

/**
 * 解析BocPublishedAt数据，并转换为项目内部可用的结构。
 * @param dateText 日期文本。
 * @param timeText 时间文本。
 * @return 处理后的结果或当前状态。
 */
private fun parseBocPublishedAt(
    dateText: String,
    timeText: String,
): LocalDateTime? {
    val normalizedDate = dateText.trim()
    val normalizedTime = timeText.trim()

    val dateTimeCandidates = buildList {
        if (normalizedDate.isNotBlank()) {
            add(normalizedDate)
        }
        if (normalizedDate.isNotBlank() && normalizedTime.isNotBlank() && !normalizedDate.contains(normalizedTime)) {
            add("$normalizedDate $normalizedTime")
        }
    }.map { it.replace(Regex("\\s+"), " ").trim() }

    dateTimeCandidates.forEach { candidate ->
        BOC_DATE_TIME_FORMATTERS.forEach { formatter ->
            runCatching { LocalDateTime.parse(candidate, formatter) }.getOrNull()?.let { return it }
        }
    }

    if (normalizedDate.isBlank()) {
        return null
    }

    val date = parseBocDate(normalizedDate.substringBefore(" ").trim()) ?: return null
    if (normalizedTime.isBlank()) {
        return date.atStartOfDay()
    }
    val time = parseBocTime(normalizedTime) ?: return null
    return date.atTime(time)
}

/**
 * 解析Boc日期数据，并转换为项目内部可用的结构。
 * @param rawDate 接口返回的原始日期文本。
 * @return 处理后的结果或当前状态。
 */
private fun parseBocDate(rawDate: String): LocalDate? {
    BOC_DATE_FORMATTERS.forEach { formatter ->
        runCatching { LocalDate.parse(rawDate, formatter) }.getOrNull()?.let { return it }
    }
    return null
}

/**
 * 解析Boc时间数据，并转换为项目内部可用的结构。
 * @param rawTime 接口返回的原始时间文本。
 * @return 处理后的结果或当前状态。
 */
private fun parseBocTime(rawTime: String): LocalTime? {
    BOC_TIME_FORMATTERS.forEach { formatter ->
        runCatching { LocalTime.parse(rawTime, formatter) }.getOrNull()?.let { return it }
    }
    return null
}

/**
 * 处理 defaultBocForexRatesUrl 相关逻辑，并返回调用方需要的结果。
 * @return 处理后的结果或当前状态。
 */
internal fun defaultBocForexRatesUrl(): String = "https://www.boc.cn/sourcedb/whpj/index.html"

internal fun defaultForexCurrencyCodes(): List<String> = listOf(
    "USD/CNY",
    "HKD/CNY",
    "EUR/CNY",
    "GBP/CNY",
    "JPY/CNY",
)

private val ISO_CURRENCY_CODE_PATTERN = Regex("""[A-Z]{3}""")

private val BOC_DATE_TIME_FORMATTERS = listOf(
    DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
    DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss"),
    DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
    DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"),
)

private val BOC_DATE_FORMATTERS = listOf(
    DateTimeFormatter.ofPattern("yyyy/MM/dd"),
    DateTimeFormatter.ofPattern("yyyy-MM-dd"),
    DateTimeFormatter.ofPattern("yyyy.MM.dd"),
)

private val BOC_TIME_FORMATTERS = listOf(
    DateTimeFormatter.ofPattern("HH:mm:ss"),
    DateTimeFormatter.ofPattern("HH:mm"),
)

private val FOREX_CURRENCY_ALIASES: Map<String, String> = linkedMapOf(
    "人民币" to "CNY",
    "RMB" to "CNY",
    "CNY" to "CNY",
    "美元" to "USD",
    "USD" to "USD",
    "欧元" to "EUR",
    "EUR" to "EUR",
    "英镑" to "GBP",
    "GBP" to "GBP",
    "港币" to "HKD",
    "HKD" to "HKD",
    "澳门元" to "MOP",
    "MOP" to "MOP",
    "日元" to "JPY",
    "JPY" to "JPY",
    "韩国元" to "KRW",
    "KRW" to "KRW",
    "新台币" to "TWD",
    "TWD" to "TWD",
    "加拿大元" to "CAD",
    "CAD" to "CAD",
    "澳大利亚元" to "AUD",
    "AUD" to "AUD",
    "新西兰元" to "NZD",
    "NZD" to "NZD",
    "新加坡元" to "SGD",
    "SGD" to "SGD",
    "瑞士法郎" to "CHF",
    "CHF" to "CHF",
    "丹麦克朗" to "DKK",
    "DKK" to "DKK",
    "挪威克朗" to "NOK",
    "NOK" to "NOK",
    "瑞典克朗" to "SEK",
    "SEK" to "SEK",
    "泰国铢" to "THB",
    "THB" to "THB",
    "卢布" to "RUB",
    "俄罗斯卢布" to "RUB",
    "RUB" to "RUB",
    "南非兰特" to "ZAR",
    "ZAR" to "ZAR",
    "菲律宾比索" to "PHP",
    "PHP" to "PHP",
    "印尼卢比" to "IDR",
    "IDR" to "IDR",
    "印度卢比" to "INR",
    "INR" to "INR",
    "土耳其里拉" to "TRY",
    "TRY" to "TRY",
    "阿联酋迪拉姆" to "AED",
    "AED" to "AED",
    "沙特里亚尔" to "SAR",
    "SAR" to "SAR",
    "巴西雷亚尔" to "BRL",
    "巴西里亚尔" to "BRL",
    "BRL" to "BRL",
    "林吉特" to "MYR",
    "马来西亚林吉特" to "MYR",
    "MYR" to "MYR",
    "捷克克朗" to "CZK",
    "CZK" to "CZK",
    "匈牙利福林" to "HUF",
    "HUF" to "HUF",
    "以色列谢克尔" to "ILS",
    "ILS" to "ILS",
    "科威特第纳尔" to "KWD",
    "KWD" to "KWD",
    "蒙古图格里克" to "MNT",
    "MNT" to "MNT",
    "墨西哥比索" to "MXN",
    "MXN" to "MXN",
    "尼泊尔卢比" to "NPR",
    "NPR" to "NPR",
    "巴基斯坦卢比" to "PKR",
    "PKR" to "PKR",
    "卡塔尔里亚尔" to "QAR",
    "QAR" to "QAR",
    "塞尔维亚第纳尔" to "RSD",
    "RSD" to "RSD",
    "越南盾" to "VND",
    "VND" to "VND",
    "文莱元" to "BND",
    "BND" to "BND",
    "柬埔寨瑞尔" to "KHR",
    "KHR" to "KHR",
)
