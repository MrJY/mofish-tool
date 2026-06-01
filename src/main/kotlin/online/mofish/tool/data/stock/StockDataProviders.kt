package online.mofish.tool.data.stock

import online.mofish.tool.data.http.MoFishHttpClient
import online.mofish.tool.domain.QuoteStatus
import online.mofish.tool.domain.StockExchange
import online.mofish.tool.domain.StockQuote
import online.mofish.tool.domain.StockSearchSuggestion
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URLEncoder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class SinaAShareQuoteProvider(
    private val httpClient: MoFishHttpClient = MoFishHttpClient(),
    private val quoteUrlProvider: (List<String>) -> String = ::defaultSinaQuoteUrl,
) : AShareQuoteProvider {
    override val providerName: String = "sina-a-share"

    /**
     * 批量获取请求资产的最新行情。
     * @param requestedStocks 需要批量查询行情的股票请求列表。
     * @return 处理后的结果或当前状态。
     */
    override fun fetchQuotes(requestedStocks: List<RequestedStock>): Map<String, StockQuote> {
        return fetchSinaQuotes(
            httpClient = httpClient,
            quoteUrlProvider = quoteUrlProvider,
            requestedStocks = requestedStocks,
            parser = ::parseSinaAShareQuote,
        )
    }
}

internal class SinaUsQuoteProvider(
    private val httpClient: MoFishHttpClient = MoFishHttpClient(),
    private val quoteUrlProvider: (List<String>) -> String = ::defaultSinaQuoteUrl,
) : UsQuoteProvider {
    override val providerName: String = "sina-us"

    /**
     * 批量获取请求资产的最新行情。
     * @param requestedStocks 需要批量查询行情的股票请求列表。
     * @return 处理后的结果或当前状态。
     */
    override fun fetchQuotes(requestedStocks: List<RequestedStock>): Map<String, StockQuote> {
        return fetchSinaQuotes(
            httpClient = httpClient,
            quoteUrlProvider = quoteUrlProvider,
            requestedStocks = requestedStocks,
            parser = ::parseSinaUsQuote,
        )
    }
}

internal class TencentAShareQuoteProvider(
    private val httpClient: MoFishHttpClient = MoFishHttpClient(),
    private val quoteUrlProvider: (List<String>) -> String = ::defaultTencentAShareQuoteUrl,
) : AShareQuoteProvider {
    override val providerName: String = "tencent-a-share"

    /**
     * 批量获取请求资产的最新行情。
     * @param requestedStocks 需要批量查询行情的股票请求列表。
     * @return 处理后的结果或当前状态。
     */
    override fun fetchQuotes(requestedStocks: List<RequestedStock>): Map<String, StockQuote> {
        if (requestedStocks.isEmpty()) {
            return emptyMap()
        }

        val responseJson = httpClient.get(
            url = quoteUrlProvider(requestedStocks.map { it.vendorCode }),
            responseCharset = TENCENT_CHARSET,
        )
        val payload = httpClient.parseJson(responseJson.body).jsonObject

        return requestedStocks.mapNotNull { requested ->
            val fields = payload[requested.vendorCode]?.jsonArray ?: return@mapNotNull null
            parseTencentAShareQuote(requested, fields)?.let { requested.originalCode to it }
        }.toMap()
    }
}

internal class TencentHongKongQuoteProvider(
    private val httpClient: MoFishHttpClient = MoFishHttpClient(),
    private val quoteUrlProvider: (List<String>) -> String = ::defaultHongKongQuoteUrl,
) : HongKongQuoteProvider {
    override val providerName: String = "tencent-hk"

    /**
     * 批量获取请求资产的最新行情。
     * @param requestedStocks 需要批量查询行情的股票请求列表。
     * @return 处理后的结果或当前状态。
     */
    override fun fetchQuotes(requestedStocks: List<RequestedStock>): Map<String, StockQuote> {
        if (requestedStocks.isEmpty()) {
            return emptyMap()
        }

        val responseJson = httpClient.get(
            url = quoteUrlProvider(requestedStocks.map { it.vendorCode }),
            responseCharset = TENCENT_CHARSET,
        )
        val payload = httpClient.parseJson(responseJson.body).jsonObject

        return requestedStocks.mapNotNull { requested ->
            val fields = payload["r_${requested.vendorCode}"]?.jsonArray ?: return@mapNotNull null
            parseTencentHongKongQuote(requested, fields)?.let { requested.originalCode to it }
        }.toMap()
    }
}

internal class TencentStockSearchSuggestionProvider(
    private val httpClient: MoFishHttpClient = MoFishHttpClient(),
    private val searchUrlProvider: (String) -> String = ::defaultStockSearchUrl,
) : StockSearchSuggestionProvider {
    override val providerName: String = "tencent-stock-search"

    /**
     * 根据用户输入搜索可添加的候选资产。
     * @param keyword 用户输入的搜索关键字。
     * @return 处理后的结果或当前状态。
     */
    override fun searchSuggestions(keyword: String): List<StockSearchSuggestion> {
        val normalizedKeyword = keyword.trim()
        if (normalizedKeyword.isEmpty()) {
            return emptyList()
        }

        val payload = httpClient.getJson(searchUrlProvider(normalizedKeyword)).jsonObject
        val stockItems = payload["data"]
            ?.jsonObject
            ?.get("stock")
            ?.jsonArray
            ?: return emptyList()

        return stockItems.mapNotNull(::toSearchSuggestion)
    }
}

/**
 * 从远程或本地数据源获取Sina行情数据。
 * @param httpClient 统一 HTTP 客户端，用于发起请求和解析响应。
 * @param quoteUrlProvider 根据资产代码列表生成行情接口地址的函数。
 * @param requestedStocks 需要批量查询行情的股票请求列表。
 * @param parser 用于把接口字段解析为领域行情模型的解析函数。
 * @return 处理后的结果或当前状态。
 */
private fun fetchSinaQuotes(
    httpClient: MoFishHttpClient,
    quoteUrlProvider: (List<String>) -> String,
    requestedStocks: List<RequestedStock>,
    parser: (RequestedStock, List<String>) -> StockQuote?,
): Map<String, StockQuote> {
    if (requestedStocks.isEmpty()) {
        return emptyMap()
    }

    val requestedByVendorCode = requestedStocks.associateBy { it.vendorCode }
    val payload = httpClient.get(
        url = quoteUrlProvider(requestedStocks.map { it.vendorCode }),
        headers = mapOf("Referer" to SINA_REFERER),
        responseCharset = SINA_CHARSET,
    ).body

    return payload
        .lineSequence()
        .mapNotNull { rawLine ->
            val line = rawLine.trim()
            val match = SINA_QUOTE_LINE.find(line) ?: return@mapNotNull null
            val vendorCode = match.groupValues[1].lowercase()
            val requested = requestedByVendorCode[vendorCode] ?: return@mapNotNull null
            val fields = match.groupValues[2].split(',')
            parser(requested, fields)?.let { requested.originalCode to it }
        }
        .toMap()
}

/**
 * 解析SinaAShare行情数据，并转换为项目内部可用的结构。
 * @param requested 已经规范化后的资产请求对象。
 * @param fields fields。
 * @return 处理后的结果或当前状态。
 */
private fun parseSinaAShareQuote(
    requested: RequestedStock,
    fields: List<String>,
): StockQuote? {
    if (fields.size <= 31) {
        return null
    }

    val previousClose = decimalValue(fields.getOrNull(2))
    var currentPrice = decimalValue(fields.getOrNull(3))
    if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) == 0) {
        currentPrice = decimalValue(fields.getOrNull(6)) ?: previousClose
    }

    return buildQuote(
        requested = requested,
        name = stringValue(fields.getOrNull(0)) ?: "${requested.originalCode} 暂无数据",
        exchange = requested.exchange,
        currentPrice = currentPrice,
        previousClose = previousClose,
        openPrice = decimalValue(fields.getOrNull(1)),
        highPrice = decimalValue(fields.getOrNull(4)),
        lowPrice = decimalValue(fields.getOrNull(5)),
        volume = decimalValue(fields.getOrNull(8)),
        turnover = decimalValue(fields.getOrNull(9)),
        updatedAt = parseSinaDateTime("${fields.getOrNull(30).orEmpty()} ${fields.getOrNull(31).orEmpty()}"),
    )
}

/**
 * 解析SinaUs行情数据，并转换为项目内部可用的结构。
 * @param requested 已经规范化后的资产请求对象。
 * @param fields fields。
 * @return 处理后的结果或当前状态。
 */
private fun parseSinaUsQuote(
    requested: RequestedStock,
    fields: List<String>,
): StockQuote? {
    if (fields.size <= 26) {
        return null
    }

    return buildQuote(
        requested = requested,
        name = stringValue(fields.getOrNull(0)) ?: "${requested.originalCode} 暂无数据",
        exchange = requested.exchange,
        currentPrice = decimalValue(fields.getOrNull(1)),
        previousClose = decimalValue(fields.getOrNull(26)),
        openPrice = decimalValue(fields.getOrNull(5)),
        highPrice = decimalValue(fields.getOrNull(6)),
        lowPrice = decimalValue(fields.getOrNull(7)),
        volume = decimalValue(fields.getOrNull(10)),
        turnover = decimalValue(fields.getOrNull(12)),
        updatedAt = parseSinaDateTime(fields.getOrNull(3).orEmpty()),
        explicitChangeAmount = decimalValue(fields.getOrNull(4)),
        explicitChangePercent = decimalValue(fields.getOrNull(2)),
        afterHoursPrice = decimalValue(fields.getOrNull(21)).takeIf { it != null && it > BigDecimal.ZERO },
        afterHoursChangePercent = decimalValue(fields.getOrNull(22)).takeIf { it != null && it != BigDecimal.ZERO },
    )
}

/**
 * 解析TencentAShare行情数据，并转换为项目内部可用的结构。
 * @param requested 已经规范化后的资产请求对象。
 * @param fields fields。
 * @return 处理后的结果或当前状态。
 */
private fun parseTencentAShareQuote(
    requested: RequestedStock,
    fields: JsonArray,
): StockQuote? {
    return buildQuote(
        requested = requested,
        name = fields.stringValue(1) ?: "${requested.originalCode} 暂无数据",
        exchange = requested.exchange,
        currentPrice = fields.decimalValue(3),
        previousClose = fields.decimalValue(4),
        openPrice = fields.decimalValue(5),
        highPrice = fields.decimalValue(33),
        lowPrice = fields.decimalValue(34),
        volume = fields.decimalValue(36)?.multiply(HUNDRED),
        turnover = fields.decimalValue(37)?.multiply(TEN_THOUSAND),
        updatedAt = parseTencentDateTime(fields.stringValue(30).orEmpty()),
        explicitChangeAmount = fields.decimalValue(31),
        explicitChangePercent = fields.decimalValue(32),
    )
}

/**
 * 解析TencentHongKong行情数据，并转换为项目内部可用的结构。
 * @param requested 已经规范化后的资产请求对象。
 * @param fields fields。
 * @return 处理后的结果或当前状态。
 */
private fun parseTencentHongKongQuote(
    requested: RequestedStock,
    fields: JsonArray,
): StockQuote? {
    return buildQuote(
        requested = requested,
        name = fields.stringValue(1) ?: "${requested.originalCode} 暂无数据",
        exchange = StockExchange.HKEX,
        currentPrice = fields.decimalValue(3),
        previousClose = fields.decimalValue(4),
        openPrice = fields.decimalValue(5),
        highPrice = fields.decimalValue(33),
        lowPrice = fields.decimalValue(34),
        volume = fields.decimalValue(36),
        turnover = fields.decimalValue(37),
        updatedAt = parseTencentSlashDateTime(fields.stringValue(30).orEmpty()),
        explicitChangeAmount = fields.decimalValue(31),
        explicitChangePercent = fields.decimalValue(32),
    )
}

/**
 * 构建行情，供后续界面展示或数据处理使用。
 * @param requested 已经规范化后的资产请求对象。
 * @param name 名称。
 * @param exchange 交易所。
 * @param currentPrice 当前Price。
 * @param previousClose previousClose。
 * @param openPrice openPrice。
 * @param highPrice highPrice。
 * @param lowPrice lowPrice。
 * @param volume volume。
 * @param turnover turnover。
 * @param updatedAt updatedAt。
 * @param explicitChangeAmount explicitChange金额。
 * @param explicitChangePercent explicitChange百分比。
 * @param afterHoursPrice afterHoursPrice。
 * @param afterHoursChangePercent afterHoursChange百分比。
 * @return 处理后的结果或当前状态。
 */
private fun buildQuote(
    requested: RequestedStock,
    name: String,
    exchange: StockExchange,
    currentPrice: BigDecimal?,
    previousClose: BigDecimal?,
    openPrice: BigDecimal?,
    highPrice: BigDecimal?,
    lowPrice: BigDecimal?,
    volume: BigDecimal?,
    turnover: BigDecimal?,
    updatedAt: LocalDateTime?,
    explicitChangeAmount: BigDecimal? = null,
    explicitChangePercent: BigDecimal? = null,
    afterHoursPrice: BigDecimal? = null,
    afterHoursChangePercent: BigDecimal? = null,
): StockQuote {
    val changeAmount = explicitChangeAmount ?: calculateChangeAmount(currentPrice, previousClose)
    val changePercent = explicitChangePercent ?: calculateChangePercent(changeAmount, previousClose)
    val status = if (currentPrice == null) QuoteStatus.UNAVAILABLE else QuoteStatus.TRADING

    return StockQuote(
        code = requested.originalCode,
        name = name,
        symbol = requested.displaySymbol,
        exchange = exchange,
        currentPrice = currentPrice,
        previousClose = previousClose,
        openPrice = openPrice,
        highPrice = highPrice,
        lowPrice = lowPrice,
        changeAmount = changeAmount,
        changePercent = changePercent,
        volume = volume,
        turnover = turnover,
        updatedAt = updatedAt,
        status = status,
        afterHoursPrice = afterHoursPrice,
        afterHoursChangePercent = afterHoursChangePercent,
    )
}

/**
 * 处理 unavailableQuote 相关逻辑，并返回调用方需要的结果。
 * @param requested 已经规范化后的资产请求对象。
 * @return 处理后的结果或当前状态。
 */
internal fun unavailableQuote(requested: RequestedStock): StockQuote {
    return StockQuote(
        code = requested.originalCode,
        name = "${requested.originalCode} 暂无数据",
        symbol = requested.displaySymbol,
        exchange = requested.exchange,
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
        status = QuoteStatus.UNAVAILABLE,
    )
}

/**
 * 转换为搜索建议表示。
 * @param item item。
 * @return 处理后的结果或当前状态。
 */
internal fun toSearchSuggestion(item: JsonElement): StockSearchSuggestion? {
    val fields = item.jsonArray
    val market = fields.stringValue(0)?.lowercase() ?: return null
    val rawCode = fields.stringValue(1) ?: return null
    val name = fields.stringValue(2) ?: return null
    val abbreviation = fields.stringValue(3)
    val category = fields.stringValue(4)?.uppercase().orEmpty()
    if (!isSupportedSearchCategory(category)) {
        return null
    }

    val requested = when (market) {
        "sh", "sz", "bj", "hk" -> normalizeRequestedStock("$market${rawCode.lowercase()}") ?: return null
        "us" -> normalizeRequestedStock("us${trimUsVendorSuffix(rawCode)}") ?: return null
        else -> return null
    }

    val description = buildList {
        add(marketLabel(requested.market))
        abbreviation?.takeIf { it.isNotBlank() }?.let { add(it) }
    }.joinToString(" | ")

    return StockSearchSuggestion(
        code = requested.originalCode,
        name = name,
        exchange = requested.exchange,
        marketLabel = marketLabel(requested.market),
        description = description,
    )
}

/**
 * 规范化Requested股票，统一后续处理使用的表示形式。
 * @param code 资产代码或业务标识。
 * @return 处理后的结果或当前状态。
 */
internal fun normalizeRequestedStock(code: String): RequestedStock? {
    val normalizedCode = canonicalizeStockInputCode(code) ?: return null
    if (normalizedCode.isBlank()) {
        return null
    }

    return when {
        normalizedCode.startsWith("sh") -> RequestedStock(
            originalCode = normalizedCode,
            vendorCode = normalizedCode,
            displaySymbol = normalizedCode.removePrefix("sh"),
            market = RequestedMarket.A,
            exchange = StockExchange.SSE,
        )
        normalizedCode.startsWith("sz") -> RequestedStock(
            originalCode = normalizedCode,
            vendorCode = normalizedCode,
            displaySymbol = normalizedCode.removePrefix("sz"),
            market = RequestedMarket.A,
            exchange = StockExchange.SZSE,
        )
        normalizedCode.startsWith("bj") -> RequestedStock(
            originalCode = normalizedCode,
            vendorCode = normalizedCode,
            displaySymbol = normalizedCode.removePrefix("bj"),
            market = RequestedMarket.A,
            exchange = StockExchange.BSE,
        )
        normalizedCode.startsWith("hk") -> RequestedStock(
            originalCode = normalizedCode,
            vendorCode = buildHongKongVendorCode(normalizedCode.removePrefix("hk")),
            displaySymbol = buildHongKongDisplaySymbol(normalizedCode.removePrefix("hk")),
            market = RequestedMarket.HK,
            exchange = StockExchange.HKEX,
        )
        normalizedCode.startsWith("gb_") -> createUsRequestedStock(
            originalCode = normalizedCode,
            vendorPrefix = "gb_",
            symbol = normalizedCode.removePrefix("gb_"),
        )
        normalizedCode.startsWith("usr_") -> createUsRequestedStock(
            originalCode = normalizedCode,
            vendorPrefix = "usr_",
            symbol = normalizedCode.removePrefix("usr_"),
        )
        normalizedCode.startsWith("us") -> createUsRequestedStock(
            originalCode = normalizedCode,
            vendorPrefix = "usr_",
            symbol = normalizedCode.removePrefix("us").trimStart('_'),
        )
        else -> null
    }
}

/**
 * 创建UsRequested股票实例或展示内容。
 * @param originalCode original代码。
 * @param vendorPrefix vendorPrefix。
 * @param symbol 行情接口使用的资产代码。
 * @return 处理后的结果或当前状态。
 */
private fun createUsRequestedStock(
    originalCode: String,
    vendorPrefix: String,
    symbol: String,
): RequestedStock? {
    val normalizedSymbol = symbol.trim().trimStart('_')
    if (normalizedSymbol.isBlank()) {
        return null
    }

    return RequestedStock(
        originalCode = originalCode,
        vendorCode = "$vendorPrefix$normalizedSymbol",
        displaySymbol = normalizedSymbol.uppercase(),
        market = RequestedMarket.US,
        exchange = inferUsExchange(normalizedSymbol),
    )
}

/**
 * 根据输入推断Us交易所。
 * @param symbol 行情接口使用的资产代码。
 * @return 处理后的结果或当前状态。
 */
private fun inferUsExchange(symbol: String): StockExchange {
    return when (symbol.lowercase()) {
        "ixic", "ndx" -> StockExchange.NASDAQ
        "dji" -> StockExchange.NYSE
        else -> StockExchange.OTHER
    }
}

/**
 * 构建HongKongVendor代码，供后续界面展示或数据处理使用。
 * @param symbol 行情接口使用的资产代码。
 * @return 处理后的结果或当前状态。
 */
private fun buildHongKongVendorCode(symbol: String): String {
    val normalizedSymbol = symbol.trim()
    if (normalizedSymbol.isBlank()) {
        return "hk"
    }
    return "hk" + if (normalizedSymbol.all(Char::isDigit)) normalizedSymbol else normalizedSymbol.uppercase()
}

/**
 * 构建HongKongDisplay代码，供后续界面展示或数据处理使用。
 * @param symbol 行情接口使用的资产代码。
 * @return 处理后的结果或当前状态。
 */
private fun buildHongKongDisplaySymbol(symbol: String): String {
    val normalizedSymbol = symbol.trim()
    return if (normalizedSymbol.all(Char::isDigit)) normalizedSymbol else normalizedSymbol.uppercase()
}

/**
 * 处理 marketLabel 相关逻辑，并返回调用方需要的结果。
 * @param market 市场。
 * @return 处理后的结果或当前状态。
 */
internal fun marketLabel(market: RequestedMarket): String {
    return when (market) {
        RequestedMarket.A -> "A股"
        RequestedMarket.HK -> "港股"
        RequestedMarket.US -> "美股"
    }
}

/**
 * 计算Change金额。
 * @param currentPrice 当前Price。
 * @param previousClose previousClose。
 * @return 处理后的结果或当前状态。
 */
private fun calculateChangeAmount(
    currentPrice: BigDecimal?,
    previousClose: BigDecimal?,
): BigDecimal? {
    if (currentPrice == null || previousClose == null) {
        return null
    }
    return currentPrice.subtract(previousClose)
}

/**
 * 计算Change百分比。
 * @param changeAmount change金额。
 * @param previousClose previousClose。
 * @return 处理后的结果或当前状态。
 */
private fun calculateChangePercent(
    changeAmount: BigDecimal?,
    previousClose: BigDecimal?,
): BigDecimal? {
    if (changeAmount == null || previousClose == null || previousClose.compareTo(BigDecimal.ZERO) == 0) {
        return null
    }
    return changeAmount
        .divide(previousClose, 6, RoundingMode.HALF_UP)
        .multiply(HUNDRED)
        .stripTrailingZeros()
}

/**
 * 解析Sina日期时间数据，并转换为项目内部可用的结构。
 * @param value 待解析、格式化或写入的原始值。
 * @return 处理后的结果或当前状态。
 */
private fun parseSinaDateTime(value: String): LocalDateTime? {
    val normalizedValue = value.trim()
    if (normalizedValue.isBlank()) {
        return null
    }

    return SINA_DATE_TIME_FORMATTERS.firstNotNullOfOrNull { formatter ->
        runCatching { LocalDateTime.parse(normalizedValue, formatter) }.getOrNull()
    }
}

/**
 * 解析Tencent日期时间数据，并转换为项目内部可用的结构。
 * @param value 待解析、格式化或写入的原始值。
 * @return 处理后的结果或当前状态。
 */
private fun parseTencentDateTime(value: String): LocalDateTime? {
    val normalizedValue = value.trim()
    if (normalizedValue.isBlank()) {
        return null
    }
    return runCatching { LocalDateTime.parse(normalizedValue, TENCENT_DATE_TIME_FORMATTER) }.getOrNull()
}

/**
 * 解析TencentSlash日期时间数据，并转换为项目内部可用的结构。
 * @param value 待解析、格式化或写入的原始值。
 * @return 处理后的结果或当前状态。
 */
private fun parseTencentSlashDateTime(value: String): LocalDateTime? {
    val normalizedValue = value.trim()
    if (normalizedValue.isBlank()) {
        return null
    }
    return runCatching { LocalDateTime.parse(normalizedValue, TENCENT_SLASH_DATE_TIME_FORMATTER) }.getOrNull()
}

/**
 * 处理 stringValue 相关逻辑，并返回调用方需要的结果。
 * @param value 待解析、格式化或写入的原始值。
 * @return 处理后的结果或当前状态。
 */
private fun stringValue(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() && it != "--" }

/**
 * 处理 decimalValue 相关逻辑，并返回调用方需要的结果。
 * @param value 待解析、格式化或写入的原始值。
 * @return 处理后的结果或当前状态。
 */
private fun decimalValue(value: String?): BigDecimal? {
    val normalizedValue = stringValue(value) ?: return null
    return normalizedValue.toBigDecimalOrNull()
}

private fun JsonArray.stringValue(index: Int): String? {
    return getOrNull(index)?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() && it != "--" }
}

private fun JsonArray.decimalValue(index: Int): BigDecimal? = stringValue(index)?.toBigDecimalOrNull()

/**
 * 处理 trimUsVendorSuffix 相关逻辑，并返回调用方需要的结果。
 * @param rawCode 用户输入或接口返回的原始资产代码。
 * @return 处理后的结果或当前状态。
 */
private fun trimUsVendorSuffix(rawCode: String): String {
    val parts = rawCode.split('.')
    if (parts.size <= 1) {
        return rawCode.lowercase()
    }
    return parts.dropLast(1).joinToString(".").lowercase()
}

/**
 * 处理 defaultSinaQuoteUrl 相关逻辑，并返回调用方需要的结果。
 * @param codes codes。
 * @return 处理后的结果或当前状态。
 */
internal fun defaultSinaQuoteUrl(codes: List<String>): String {
    return "https://hq.sinajs.cn/list=${codes.joinToString(",")}"
}

/**
 * 处理 defaultTencentAShareQuoteUrl 相关逻辑，并返回调用方需要的结果。
 * @param codes codes。
 * @return 处理后的结果或当前状态。
 */
internal fun defaultTencentAShareQuoteUrl(codes: List<String>): String {
    return "https://qt.gtimg.cn/q=${codes.joinToString(",")}&fmt=json"
}

/**
 * 处理 defaultHongKongQuoteUrl 相关逻辑，并返回调用方需要的结果。
 * @param codes codes。
 * @return 处理后的结果或当前状态。
 */
internal fun defaultHongKongQuoteUrl(codes: List<String>): String {
    val query = codes.joinToString(",") { "r_$it" }
    return "https://qt.gtimg.cn/q=$query&fmt=json"
}

/**
 * 处理 defaultStockSearchUrl 相关逻辑，并返回调用方需要的结果。
 * @param keyword 用户输入的搜索关键字。
 * @return 处理后的结果或当前状态。
 */
internal fun defaultStockSearchUrl(keyword: String): String {
    val encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8)
    return "https://proxy.finance.qq.com/ifzqgtimg/appstock/smartbox/search/get?q=$encodedKeyword"
}

/**
 * 判断是否满足Supported搜索Category条件。
 * @param category category。
 * @return 处理后的结果或当前状态。
 */
private fun isSupportedSearchCategory(category: String): Boolean {
    val normalizedCategory = category.trim().uppercase()
    if (normalizedCategory.isBlank()) {
        return false
    }

    return normalizedCategory == "GP" ||
        normalizedCategory.startsWith("GP-") ||
        normalizedCategory == "ZS" ||
        normalizedCategory.contains("ETF") ||
        normalizedCategory.contains("LOF")
}

internal data class RequestedStock(
    val originalCode: String,
    val vendorCode: String,
    val displaySymbol: String,
    val market: RequestedMarket,
    val exchange: StockExchange,
)

internal enum class RequestedMarket {
    A,
    HK,
    US,
}

private val SINA_CHARSET: Charset = Charset.forName("GB18030")
private val TENCENT_CHARSET: Charset = Charset.forName("GBK")
private val HUNDRED = BigDecimal("100")
private val TEN_THOUSAND = BigDecimal("10000")
private val SINA_DATE_TIME_FORMATTERS = listOf(
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
    DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
)
private val TENCENT_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
private val TENCENT_SLASH_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
private val SINA_QUOTE_LINE = Regex("""var hq_str_([^=]+)="(.*)";?""")
private const val SINA_REFERER = "https://finance.sina.com.cn/"
