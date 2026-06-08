package online.mofish.tool.data.crypto

import online.mofish.tool.data.http.MoFishHttpClient
import online.mofish.tool.domain.CryptoQuote
import online.mofish.tool.domain.CryptoSearchSuggestion
import online.mofish.tool.domain.QuoteStatus
import java.math.BigDecimal
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class CoinGeckoCryptoQuoteProvider(
    private val httpClient: MoFishHttpClient = MoFishHttpClient(),
    private val vsCurrency: String = DEFAULT_VS_CURRENCY,
    private val marketsUrlProvider: (String, List<String>) -> String = ::defaultCoinGeckoMarketsUrl,
) : CryptoQuoteProvider {
    override val providerName: String = "coingecko-markets"

    /**
     * 批量获取请求资产的最新行情。
     * @param codes codes。
     * @return 处理后的结果或当前状态。
     */
    override fun fetchQuotes(codes: List<String>): Map<String, CryptoQuote> {
        val normalizedCodes = codes
            .map(::normalizeCryptoCode)
            .filter { it.isNotEmpty() }
            .distinct()
        if (normalizedCodes.isEmpty()) {
            return emptyMap()
        }

        return httpClient.getJson(marketsUrlProvider(vsCurrency, normalizedCodes))
            .jsonArray
            .mapNotNull { item ->
                item.jsonObject.toCryptoQuote(vsCurrency)
            }
            .associateBy { it.code }
    }
}

class CoinGeckoCryptoSearchIndexProvider(
    private val httpClient: MoFishHttpClient = MoFishHttpClient(),
    private val listUrlProvider: () -> String = ::defaultCoinGeckoListUrl,
) : CryptoSearchIndexProvider {
    override val providerName: String = "coingecko-list"

    /**
     * 加载Index数据。
     * @return 处理后的结果或当前状态。
     */
    override fun loadIndex(): List<CryptoSearchSuggestion> {
        return httpClient.getJson(listUrlProvider())
            .jsonArray
            .mapNotNull(::toCryptoSearchSuggestion)
    }
}

class BinanceCryptoQuoteProvider(
    private val httpClient: MoFishHttpClient = MoFishHttpClient(),
    private val tickerUrlProvider: (String) -> String = ::defaultBinanceTicker24hUrl,
) : CryptoQuoteProvider {
    override val providerName: String = "binance-24hr"

    /**
     * 批量获取请求资产的最新行情。
     * @param codes codes。
     * @return 处理后的结果或当前状态。
     */
    override fun fetchQuotes(codes: List<String>): Map<String, CryptoQuote> {
        return codes
            .map(::normalizeCryptoCode)
            .filter { it.isNotEmpty() }
            .distinct()
            .mapNotNull { code ->
                val tickerSymbol = BINANCE_USDT_SYMBOLS_BY_CODE[code] ?: return@mapNotNull null
                runCatching {
                    parseBinanceTickerQuote(
                        code = code,
                        tickerSymbol = tickerSymbol,
                        payload = httpClient.getJson(tickerUrlProvider(tickerSymbol)).jsonObject,
                    )
                }.getOrNull()
            }
            .associateBy { it.code }
    }
}

class CachedCryptoSearchIndexProvider(
    private val delegate: CryptoSearchIndexProvider,
) : CryptoSearchIndexProvider {
    override val providerName: String = "cached:${delegate.providerName}"

    @Volatile
    private var cache: List<CryptoSearchSuggestion>? = null

    /**
     * 加载Index数据。
     * @return 处理后的结果或当前状态。
     */
    override fun loadIndex(): List<CryptoSearchSuggestion> {
        val cached = cache
        if (cached != null) {
            return cached
        }

        return synchronized(this) {
            val secondRead = cache
            if (secondRead != null) {
                secondRead
            } else {
                delegate.loadIndex().also { cache = it }
            }
        }
    }
}

private fun JsonObject.toCryptoQuote(vsCurrency: String): CryptoQuote? {
    val code = stringValue("id") ?: return null
    val name = stringValue("name") ?: code
    val symbol = stringValue("symbol")?.uppercase() ?: code.uppercase()
    val currentPrice = decimalValue("current_price")

    return CryptoQuote(
        code = code,
        symbol = symbol,
        name = name,
        currentPrice = currentPrice,
        marketCap = decimalValue("market_cap"),
        totalVolume = decimalValue("total_volume"),
        priceChangePercentage24h = decimalValue("price_change_percentage_24h"),
        circulatingSupply = decimalValue("circulating_supply"),
        updatedAt = dateTimeValue("last_updated"),
        quoteCurrency = vsCurrency.uppercase(),
        status = if (currentPrice == null) QuoteStatus.UNAVAILABLE else QuoteStatus.TRADING,
    )
}

/**
 * 转换为虚拟币搜索建议表示。
 * @param item item。
 * @return 处理后的结果或当前状态。
 */
private fun toCryptoSearchSuggestion(item: JsonElement): CryptoSearchSuggestion? {
    val payload = item.jsonObject
    val code = payload.stringValue("id") ?: return null
    val name = payload.stringValue("name") ?: return null
    val symbol = payload.stringValue("symbol")?.uppercase() ?: return null
    return CryptoSearchSuggestion(
        code = code,
        symbol = symbol,
        name = name,
    )
}

/**
 * 转换 Binance 24hr ticker 为虚拟币行情。
 * @param code 资产代码或业务标识。
 * @param tickerSymbol Binance 交易对符号。
 * @param payload payload。
 * @return 处理后的结果或当前状态。
 */
internal fun parseBinanceTickerQuote(
    code: String,
    tickerSymbol: String,
    payload: JsonObject,
): CryptoQuote? {
    val definition = BINANCE_CRYPTO_DEFINITIONS_BY_CODE[code] ?: return null
    val responseSymbol = payload.stringValue("symbol")
    if (responseSymbol != null && !responseSymbol.equals(tickerSymbol, ignoreCase = true)) {
        return null
    }
    val currentPrice = payload.decimalValue("lastPrice") ?: return null
    return CryptoQuote(
        code = code,
        symbol = definition.symbol,
        name = definition.name,
        currentPrice = currentPrice,
        marketCap = null,
        totalVolume = payload.decimalValue("quoteVolume"),
        priceChangePercentage24h = payload.decimalValue("priceChangePercent"),
        circulatingSupply = null,
        updatedAt = payload.epochMillisDateTimeValue("closeTime"),
        quoteCurrency = "USD",
        status = QuoteStatus.TRADING,
    )
}

private fun JsonObject.stringValue(key: String): String? {
    return this[key]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() }
}

private fun JsonObject.decimalValue(key: String): BigDecimal? {
    return stringValue(key)?.toBigDecimalOrNull()
}

private fun JsonObject.dateTimeValue(key: String): LocalDateTime? {
    val raw = stringValue(key) ?: return null
    return runCatching {
        LocalDateTime.ofInstant(Instant.parse(raw), ZoneId.systemDefault())
    }.getOrNull()
}

private fun JsonObject.epochMillisDateTimeValue(key: String): LocalDateTime? {
    val millis = stringValue(key)?.toLongOrNull() ?: return null
    return runCatching {
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
    }.getOrNull()
}

/**
 * 处理 defaultCoinGeckoListUrl 相关逻辑，并返回调用方需要的结果。
 * @return 处理后的结果或当前状态。
 */
internal fun defaultCoinGeckoListUrl(): String = "https://api.coingecko.com/api/v3/coins/list"

/**
 * 处理 defaultCoinGeckoMarketsUrl 相关逻辑，并返回调用方需要的结果。
 * @param vsCurrency vs币种。
 * @param ids ids。
 * @return 处理后的结果或当前状态。
 */
internal fun defaultCoinGeckoMarketsUrl(
    vsCurrency: String,
    ids: List<String>,
): String {
    val encodedCurrency = URLEncoder.encode(vsCurrency.trim().lowercase(), Charsets.UTF_8)
    val encodedIds = ids.joinToString(",") { URLEncoder.encode(it, Charsets.UTF_8) }
    val perPage = ids.size.coerceIn(1, 250)
    return buildString {
        append("https://api.coingecko.com/api/v3/coins/markets")
        append("?vs_currency=").append(encodedCurrency)
        append("&ids=").append(encodedIds)
        append("&order=market_cap_desc")
        append("&per_page=").append(perPage)
        append("&page=1")
        append("&price_change_percentage=24h")
    }
}

/**
 * 构建 Binance 24hr ticker URL。
 * @param symbol Binance 交易对符号。
 * @return 处理后的结果或当前状态。
 */
internal fun defaultBinanceTicker24hUrl(symbol: String): String {
    val encodedSymbol = URLEncoder.encode(symbol.trim().uppercase(), Charsets.UTF_8)
    return "https://api.binance.com/api/v3/ticker/24hr?symbol=$encodedSymbol"
}

/**
 * 规范化虚拟币代码，统一后续处理使用的表示形式。
 * @param rawCode 用户输入或接口返回的原始资产代码。
 * @return 处理后的结果或当前状态。
 */
internal fun normalizeCryptoCode(rawCode: String): String = rawCode.trim().lowercase()

internal const val DEFAULT_VS_CURRENCY = "usd"

private data class BinanceCryptoDefinition(
    val symbol: String,
    val name: String,
    val tickerSymbol: String,
)

private val BINANCE_CRYPTO_DEFINITIONS_BY_CODE = mapOf(
    "bitcoin" to BinanceCryptoDefinition("BTC", "Bitcoin", "BTCUSDT"),
    "ethereum" to BinanceCryptoDefinition("ETH", "Ethereum", "ETHUSDT"),
    "binancecoin" to BinanceCryptoDefinition("BNB", "BNB", "BNBUSDT"),
    "solana" to BinanceCryptoDefinition("SOL", "Solana", "SOLUSDT"),
    "ripple" to BinanceCryptoDefinition("XRP", "XRP", "XRPUSDT"),
    "cardano" to BinanceCryptoDefinition("ADA", "Cardano", "ADAUSDT"),
    "dogecoin" to BinanceCryptoDefinition("DOGE", "Dogecoin", "DOGEUSDT"),
    "tron" to BinanceCryptoDefinition("TRX", "TRON", "TRXUSDT"),
    "chainlink" to BinanceCryptoDefinition("LINK", "Chainlink", "LINKUSDT"),
    "polkadot" to BinanceCryptoDefinition("DOT", "Polkadot", "DOTUSDT"),
    "litecoin" to BinanceCryptoDefinition("LTC", "Litecoin", "LTCUSDT"),
    "avalanche-2" to BinanceCryptoDefinition("AVAX", "Avalanche", "AVAXUSDT"),
    "the-open-network" to BinanceCryptoDefinition("TON", "Toncoin", "TONUSDT"),
    "shiba-inu" to BinanceCryptoDefinition("SHIB", "Shiba Inu", "SHIBUSDT"),
    "sui" to BinanceCryptoDefinition("SUI", "Sui", "SUIUSDT"),
)

private val BINANCE_USDT_SYMBOLS_BY_CODE = BINANCE_CRYPTO_DEFINITIONS_BY_CODE.mapValues { it.value.tickerSymbol }
