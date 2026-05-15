package online.mofish.tool.data

import online.mofish.tool.data.index.MarketIndexDefinition
import online.mofish.tool.data.index.defaultMarketIndexDefinitions
import online.mofish.tool.data.index.marketIndexDefinitionFor
import online.mofish.tool.domain.CryptoQuote
import online.mofish.tool.domain.FlashNewsImpact
import online.mofish.tool.domain.FlashNewsItem
import online.mofish.tool.domain.FlashNewsSource
import online.mofish.tool.domain.ForexRate
import online.mofish.tool.domain.FundQuote
import online.mofish.tool.domain.MoFishWorkspace
import online.mofish.tool.domain.QuoteStatus
import online.mofish.tool.domain.StockExchange
import online.mofish.tool.domain.StockQuote
import online.mofish.tool.settings.MoFishSettingsState
import online.mofish.tool.settings.MoFishWatchlistSettings
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class StaticMoFishDataSource : MoFishDataSource {
    override fun createSkeletonWorkspace(
        projectName: String,
        settings: MoFishSettingsState,
    ): MoFishWorkspace {
        return MoFishWorkspace(
            projectName = projectName,
            fundQuotes = settings.watchlist.fundCodes.map { placeholderFundQuote(it, settings) },
            stockQuotes = settings.watchlist.stockCodes.map { placeholderStockQuote(it, settings) },
            cryptoQuotes = settings.watchlist.cryptoIds.map { placeholderCryptoQuote(it, settings) },
            holdings = settings.holdings,
            reminderRules = settings.reminders,
            aiConfig = settings.aiConfig,
            flashNews = emptyList(),
            forexRates = emptyList(),
            indexQuotes = defaultMarketIndexDefinitions().map(::placeholderIndexQuote),
        )
    }

    override fun loadWorkspace(
        projectName: String,
        settings: MoFishSettingsState,
    ): MoFishWorkspace {
        return MoFishWorkspace(
            projectName = projectName,
            fundQuotes = buildStaticFundQuotes(settings),
            stockQuotes = buildStaticStockQuotes(settings.watchlist, settings),
            cryptoQuotes = buildStaticCryptoQuotes(settings.watchlist, settings),
            holdings = settings.holdings,
            reminderRules = settings.reminders,
            aiConfig = settings.aiConfig,
            flashNews = sampleFlashNews(),
            forexRates = sampleForexRates(),
            indexQuotes = sampleIndexQuotes(),
        )
    }
}

private fun buildStaticFundQuotes(settings: MoFishSettingsState): List<FundQuote> {
    val codes = settings.watchlist.fundCodes.ifEmpty { listOf(DEFAULT_FUND_CODE) }
    return codes.map { code ->
        if (code == DEFAULT_FUND_CODE) {
            sampleFundQuote()
        } else {
            placeholderFundQuote(code, settings)
        }
    }
}

private fun buildStaticStockQuotes(
    watchlist: MoFishWatchlistSettings,
    settings: MoFishSettingsState,
): List<StockQuote> {
    val codes = watchlist.stockCodes.ifEmpty { listOf(DEFAULT_STOCK_CODE) }
    return codes.map { code ->
        if (code.equals(DEFAULT_STOCK_CODE, ignoreCase = true)) {
            sampleStockQuote()
        } else {
            placeholderStockQuote(code, settings)
        }
    }
}

private fun buildStaticCryptoQuotes(
    watchlist: MoFishWatchlistSettings,
    settings: MoFishSettingsState,
): List<CryptoQuote> {
    val codes = watchlist.cryptoIds.ifEmpty { listOf(DEFAULT_CRYPTO_CODE) }
    return codes.map { code ->
        if (code.equals(DEFAULT_CRYPTO_CODE, ignoreCase = true)) {
            sampleCryptoQuote()
        } else {
            placeholderCryptoQuote(code, settings)
        }
    }
}

private fun sampleFundQuote(): FundQuote {
    return FundQuote(
        code = DEFAULT_FUND_CODE,
        name = DEFAULT_FUND_NAME,
        estimatedNetValue = decimal("0.8123"),
        previousNetValue = decimal("0.8050"),
        dailyChangePercent = decimal("0.91"),
        valuationTime = LocalDateTime.of(2026, 3, 24, 14, 55),
        netValueDate = LocalDate.of(2026, 3, 23),
        status = QuoteStatus.TRADING,
    )
}

private fun sampleStockQuote(): StockQuote {
    return StockQuote(
        code = DEFAULT_STOCK_CODE,
        name = DEFAULT_STOCK_NAME,
        symbol = "300750",
        exchange = StockExchange.SZSE,
        currentPrice = decimal("214.32"),
        previousClose = decimal("210.01"),
        openPrice = decimal("211.00"),
        highPrice = decimal("215.45"),
        lowPrice = decimal("209.80"),
        changeAmount = decimal("4.31"),
        changePercent = decimal("2.05"),
        volume = decimal("15324567"),
        turnover = decimal("3278456000"),
        updatedAt = LocalDateTime.of(2026, 3, 24, 14, 58),
        status = QuoteStatus.TRADING,
        afterHoursPrice = null,
        afterHoursChangePercent = null,
    )
}

private fun sampleCryptoQuote(): CryptoQuote {
    return CryptoQuote(
        code = DEFAULT_CRYPTO_CODE,
        symbol = "BTC",
        name = DEFAULT_CRYPTO_NAME,
        currentPrice = decimal("67250.12"),
        marketCap = decimal("1320000000000"),
        totalVolume = decimal("28950000000"),
        priceChangePercentage24h = decimal("2.18"),
        circulatingSupply = decimal("19620000"),
        updatedAt = LocalDateTime.of(2026, 3, 24, 21, 18),
        quoteCurrency = "USD",
        status = QuoteStatus.TRADING,
    )
}

private fun placeholderFundQuote(
    code: String,
    settings: MoFishSettingsState,
): FundQuote {
    return FundQuote(
        code = code,
        name = displayNameFor(code, settings) ?: "$code 暂无数据",
        estimatedNetValue = null,
        previousNetValue = null,
        dailyChangePercent = null,
        valuationTime = null,
        netValueDate = null,
        status = QuoteStatus.UNAVAILABLE,
        isEstimated = false,
    )
}

private fun placeholderStockQuote(
    code: String,
    settings: MoFishSettingsState,
): StockQuote {
    return StockQuote(
        code = code,
        name = displayNameFor(code, settings) ?: "$code 暂无数据",
        symbol = normalizeStockSymbol(code),
        exchange = inferExchange(code),
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

private fun placeholderIndexQuote(definition: MarketIndexDefinition): StockQuote {
    return StockQuote(
        code = definition.code,
        name = definition.displayName,
        symbol = normalizeStockSymbol(definition.code).uppercase(),
        exchange = inferExchange(definition.code),
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

private fun placeholderCryptoQuote(
    code: String,
    settings: MoFishSettingsState,
): CryptoQuote {
    return CryptoQuote(
        code = code,
        symbol = code.uppercase(),
        name = displayNameFor(code, settings) ?: "$code 暂无数据",
        currentPrice = null,
        marketCap = null,
        totalVolume = null,
        priceChangePercentage24h = null,
        circulatingSupply = null,
        updatedAt = null,
        quoteCurrency = "USD",
        status = QuoteStatus.UNAVAILABLE,
    )
}

private fun displayNameFor(
    code: String,
    settings: MoFishSettingsState,
): String? {
    return settings.holdings.firstOrNull { it.code.equals(code, ignoreCase = true) }?.displayName
        ?: settings.reminders.firstOrNull { it.code.equals(code, ignoreCase = true) }?.displayName
        ?: marketIndexDefinitionFor(code)?.displayName
}

private fun inferExchange(code: String): StockExchange {
    val normalized = code.lowercase()
    return when {
        normalized.startsWith("sh") -> StockExchange.SSE
        normalized.startsWith("sz") -> StockExchange.SZSE
        normalized.startsWith("bj") -> StockExchange.BSE
        normalized.startsWith("hk") -> StockExchange.HKEX
        normalized.startsWith("us") -> StockExchange.NASDAQ
        else -> StockExchange.OTHER
    }
}

private fun normalizeStockSymbol(code: String): String {
    return code
        .removePrefix("sh")
        .removePrefix("sz")
        .removePrefix("bj")
        .removePrefix("hk")
        .removePrefix("us")
}

private fun sampleFlashNews(): List<FlashNewsItem> {
    return listOf(
        FlashNewsItem(
            id = "xgb-1001",
            source = FlashNewsSource.XUANGUBAO,
            title = "新能源板块盘中异动",
            summary = "多只储能与电池链个股拉升，市场开始关注排产与出口预期。",
            occurredAt = LocalDateTime.of(2026, 3, 24, 10, 32),
            impact = FlashNewsImpact.POSITIVE,
            important = true,
            relatedBoards = listOf("新能源车", "储能"),
            relatedStocks = listOf("宁德时代 300750", "亿纬锂能 300014"),
            tags = listOf("sector", "battery"),
        ),
        FlashNewsItem(
            id = "jin10-1002",
            source = FlashNewsSource.JIN10,
            title = "海外宏观数据公布",
            summary = "市场等待晚间数据落地，成长赛道成交继续放大。",
            occurredAt = LocalDateTime.of(2026, 3, 24, 20, 30),
            impact = FlashNewsImpact.NEUTRAL,
            important = false,
            tags = listOf("macro"),
        ),
    )
}

private fun sampleForexRates(): List<ForexRate> {
    return listOf(
        ForexRate(
            currencyName = "美元",
            currencyCode = "USD/CNY",
            spotBuyPrice = decimal("722.14"),
            cashBuyPrice = decimal("716.21"),
            spotSellPrice = decimal("725.03"),
            cashSellPrice = decimal("725.03"),
            conversionPrice = decimal("723.48"),
            publishedAt = LocalDateTime.of(2026, 3, 24, 9, 32),
        ),
        ForexRate(
            currencyName = "港币",
            currencyCode = "HKD/CNY",
            spotBuyPrice = decimal("92.34"),
            cashBuyPrice = decimal("91.60"),
            spotSellPrice = decimal("92.71"),
            cashSellPrice = decimal("92.71"),
            conversionPrice = decimal("92.53"),
            publishedAt = LocalDateTime.of(2026, 3, 24, 9, 32),
        ),
    )
}

private fun sampleIndexQuotes(): List<StockQuote> {
    return listOf(
        StockQuote(
            code = "sh000001",
            name = "上证指数",
            symbol = "000001",
            exchange = StockExchange.SSE,
            currentPrice = decimal("3924.96"),
            previousClose = decimal("3931.84"),
            openPrice = decimal("3889.08"),
            highPrice = decimal("3937.10"),
            lowPrice = decimal("3880.57"),
            changeAmount = decimal("-6.88"),
            changePercent = decimal("-0.17"),
            volume = decimal("615973981"),
            turnover = decimal("848361335072"),
            updatedAt = LocalDateTime.of(2026, 3, 26, 15, 30, 39),
            status = QuoteStatus.TRADING,
        ),
        StockQuote(
            code = "sz399001",
            name = "深证成指",
            symbol = "399001",
            exchange = StockExchange.SZSE,
            currentPrice = decimal("13756.87"),
            previousClose = decimal("13801.00"),
            openPrice = decimal("13606.44"),
            highPrice = decimal("13853.10"),
            lowPrice = decimal("13573.12"),
            changeAmount = decimal("-44.13"),
            changePercent = decimal("-0.32"),
            volume = decimal("66666803450"),
            turnover = decimal("1095241403150.813"),
            updatedAt = LocalDateTime.of(2026, 3, 26, 15, 0, 3),
            status = QuoteStatus.TRADING,
        ),
        StockQuote(
            code = "sz399006",
            name = "创业板指",
            symbol = "399006",
            exchange = StockExchange.SZSE,
            currentPrice = decimal("3299.03"),
            previousClose = decimal("3316.97"),
            openPrice = decimal("3272.49"),
            highPrice = decimal("3347.23"),
            lowPrice = decimal("3263.99"),
            changeAmount = decimal("-17.94"),
            changePercent = decimal("-0.54"),
            volume = decimal("18545832832"),
            turnover = decimal("489469562687.770"),
            updatedAt = LocalDateTime.of(2026, 3, 26, 15, 0, 3),
            status = QuoteStatus.TRADING,
        ),
        StockQuote(
            code = "sh000300",
            name = "沪深300",
            symbol = "000300",
            exchange = StockExchange.SSE,
            currentPrice = decimal("4525.33"),
            previousClose = decimal("4537.47"),
            openPrice = decimal("4477.53"),
            highPrice = decimal("4541.05"),
            lowPrice = decimal("4468.96"),
            changeAmount = decimal("-12.14"),
            changePercent = decimal("-0.27"),
            volume = decimal("213736788"),
            turnover = decimal("471127498414"),
            updatedAt = LocalDateTime.of(2026, 3, 26, 15, 35, 29),
            status = QuoteStatus.TRADING,
        ),
        StockQuote(
            code = "sh000688",
            name = "科创50",
            symbol = "000688",
            exchange = StockExchange.SSE,
            currentPrice = decimal("1313.97"),
            previousClose = decimal("1315.41"),
            openPrice = decimal("1288.81"),
            highPrice = decimal("1316.46"),
            lowPrice = decimal("1285.38"),
            changeAmount = decimal("-1.45"),
            changePercent = decimal("-0.11"),
            volume = decimal("8631770"),
            turnover = decimal("55519453245"),
            updatedAt = LocalDateTime.of(2026, 3, 26, 15, 35, 35),
            status = QuoteStatus.TRADING,
        ),
        StockQuote(
            code = "hkhsi",
            name = "恒生指数",
            symbol = "HSI",
            exchange = StockExchange.HKEX,
            currentPrice = decimal("24813.660"),
            previousClose = decimal("25335.950"),
            openPrice = decimal("25267.160"),
            highPrice = decimal("25278.030"),
            lowPrice = decimal("24795.060"),
            changeAmount = decimal("-522.290"),
            changePercent = decimal("-2.06"),
            volume = decimal("0"),
            turnover = decimal("24122879.4704"),
            updatedAt = LocalDateTime.of(2026, 3, 26, 15, 48, 58),
            status = QuoteStatus.TRADING,
        ),
        StockQuote(
            code = "usixic",
            name = "纳斯达克综合指数",
            symbol = "IXIC",
            exchange = StockExchange.NASDAQ,
            currentPrice = decimal("21929.8254"),
            previousClose = decimal("21761.8943"),
            openPrice = decimal("22006.4278"),
            highPrice = decimal("22093.1802"),
            lowPrice = decimal("21865.4552"),
            changeAmount = decimal("167.9311"),
            changePercent = decimal("0.77"),
            volume = decimal("6785904233"),
            turnover = decimal("6663570376"),
            updatedAt = LocalDateTime.of(2026, 3, 26, 5, 30),
            status = QuoteStatus.TRADING,
        ),
    )
}

private const val DEFAULT_FUND_CODE = "161725"
private const val DEFAULT_FUND_NAME = "招商中证白酒指数(LOF)A"
private const val DEFAULT_STOCK_CODE = "sz300750"
private const val DEFAULT_STOCK_NAME = "宁德时代"
private const val DEFAULT_CRYPTO_CODE = "bitcoin"
private const val DEFAULT_CRYPTO_NAME = "Bitcoin"

private fun decimal(value: String): BigDecimal = BigDecimal(value)
