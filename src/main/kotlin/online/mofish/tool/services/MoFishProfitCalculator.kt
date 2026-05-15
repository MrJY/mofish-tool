package online.mofish.tool.services

import online.mofish.tool.domain.AssetProfitSummary
import online.mofish.tool.domain.AssetType
import online.mofish.tool.domain.CryptoQuote
import online.mofish.tool.domain.ForexRate
import online.mofish.tool.domain.FundQuote
import online.mofish.tool.domain.HoldingConfig
import online.mofish.tool.domain.MoFishWorkspace
import online.mofish.tool.domain.PositionProfitSnapshot
import online.mofish.tool.domain.StockExchange
import online.mofish.tool.domain.StockQuote
import online.mofish.tool.domain.WorkspaceProfitSnapshot
import java.math.BigDecimal
import java.math.RoundingMode

class MoFishProfitCalculator {
    fun calculate(workspace: MoFishWorkspace): WorkspaceProfitSnapshot {
        val fundQuotesByCode = workspace.fundQuotes.associateBy { it.code }
        val stockQuotesByCode = workspace.stockQuotes.associateBy { it.code }
        val cryptoQuotesByCode = workspace.cryptoQuotes.associateBy { it.code }
        val forexContext = buildForexContext(workspace.forexRates)

        return WorkspaceProfitSnapshot(
            fundSummary = calculateFundSummary(workspace.holdings, fundQuotesByCode, forexContext),
            stockSummary = calculateStockSummary(workspace.holdings, stockQuotesByCode, forexContext),
            cryptoSummary = calculateCryptoSummary(workspace.holdings, cryptoQuotesByCode, forexContext),
        )
    }

    private fun calculateFundSummary(
        holdings: List<HoldingConfig>,
        quotesByCode: Map<String, FundQuote>,
        forexContext: ForexConversionContext,
    ): AssetProfitSummary {
        val items = holdings
            .filter { it.assetType == AssetType.FUND && it.hasPosition }
            .map { holding ->
                val quote = quotesByCode[holding.code]
                calculateFundPosition(holding, quote, forexContext)
            }
        return summarize(AssetType.FUND, items)
    }

    private fun calculateStockSummary(
        holdings: List<HoldingConfig>,
        quotesByCode: Map<String, StockQuote>,
        forexContext: ForexConversionContext,
    ): AssetProfitSummary {
        val items = holdings
            .filter { it.assetType == AssetType.STOCK && it.hasPosition }
            .map { holding ->
                val quote = quotesByCode[holding.code]
                calculateStockPosition(holding, quote, forexContext)
            }
        return summarize(AssetType.STOCK, items)
    }

    private fun calculateCryptoSummary(
        holdings: List<HoldingConfig>,
        quotesByCode: Map<String, CryptoQuote>,
        forexContext: ForexConversionContext,
    ): AssetProfitSummary {
        val items = holdings
            .filter { it.assetType == AssetType.CRYPTO && it.hasPosition }
            .map { holding ->
                val quote = quotesByCode[holding.code]
                calculateCryptoPosition(holding, quote, forexContext)
            }
        return summarize(AssetType.CRYPTO, items)
    }

    private fun calculateFundPosition(
        holding: HoldingConfig,
        quote: FundQuote?,
        forexContext: ForexConversionContext,
    ): PositionProfitSnapshot {
        val quantity = holding.quantity
        val holdingCurrency = normalizeCurrency(holding.currency, forexContext) ?: DEFAULT_CURRENCY
        val costAmount = convertAmountToCny(resolveCostAmount(holding), holdingCurrency, forexContext)
        val currentUnitValue = quote?.estimatedNetValue ?: quote?.previousNetValue
        val previousUnitValue = quote?.previousNetValue ?: currentUnitValue
        val currentValue = convertAmountToCny(multiplyOrNull(quantity, currentUnitValue), holdingCurrency, forexContext)
        val previousValue = convertAmountToCny(multiplyOrNull(quantity, previousUnitValue), holdingCurrency, forexContext)
        val totalProfit = subtractOrNull(currentValue, costAmount)
        val todayProfit = subtractOrNull(currentValue, previousValue)

        return PositionProfitSnapshot(
            holdingId = holding.id,
            assetType = AssetType.FUND,
            code = holding.code,
            displayName = holding.displayName,
            quantity = quantity,
            costAmount = costAmount,
            currentValue = currentValue,
            totalProfit = totalProfit,
            totalProfitPercent = ratio(totalProfit, costAmount),
            todayProfit = todayProfit,
            todayProfitPercent = ratio(todayProfit, previousValue),
            quoteAvailable = currentValue != null,
        )
    }

    private fun calculateStockPosition(
        holding: HoldingConfig,
        quote: StockQuote?,
        forexContext: ForexConversionContext,
    ): PositionProfitSnapshot {
        val quantity = holding.quantity
        val holdingCurrency = normalizeCurrency(holding.currency, forexContext) ?: DEFAULT_CURRENCY
        val quoteCurrency = resolveStockQuoteCurrency(holding, quote, forexContext)
        val costAmount = convertAmountToCny(resolveCostAmount(holding), holdingCurrency, forexContext)
        val currentUnitPrice = quote?.currentPrice ?: quote?.afterHoursPrice
        val previousUnitPrice = quote?.previousClose ?: quote?.openPrice ?: holding.costPrice
        val todayBaseUnitPrice = holding.todayCostPrice ?: previousUnitPrice
        val currentValue = convertAmountToCny(multiplyOrNull(quantity, currentUnitPrice), quoteCurrency, forexContext)
        val todayBaseCurrency = if (holding.todayCostPrice != null) holdingCurrency else quoteCurrency
        val todayBaseValue = convertAmountToCny(multiplyOrNull(quantity, todayBaseUnitPrice), todayBaseCurrency, forexContext)
        val totalProfit = subtractOrNull(currentValue, costAmount)
        val todayProfit = subtractOrNull(currentValue, todayBaseValue)

        return PositionProfitSnapshot(
            holdingId = holding.id,
            assetType = AssetType.STOCK,
            code = holding.code,
            displayName = holding.displayName,
            quantity = quantity,
            costAmount = costAmount,
            currentValue = currentValue,
            totalProfit = totalProfit,
            totalProfitPercent = ratio(totalProfit, costAmount),
            todayProfit = todayProfit,
            todayProfitPercent = ratio(todayProfit, todayBaseValue),
            quoteAvailable = currentValue != null,
        )
    }

    private fun calculateCryptoPosition(
        holding: HoldingConfig,
        quote: CryptoQuote?,
        forexContext: ForexConversionContext,
    ): PositionProfitSnapshot {
        val quantity = holding.quantity
        val holdingCurrency = normalizeCurrency(holding.currency, forexContext) ?: DEFAULT_CURRENCY
        val quoteCurrency = normalizeCurrency(quote?.quoteCurrency, forexContext) ?: holdingCurrency
        val costAmount = convertAmountToCny(resolveCostAmount(holding), holdingCurrency, forexContext)
        val currentUnitPrice = quote?.currentPrice
        val derivedPreviousUnitPrice = deriveCryptoPreviousPrice(quote)
        val configuredBaseUnitPrice = holding.todayCostPrice ?: holding.costPrice
        val currentValue = convertAmountToCny(multiplyOrNull(quantity, currentUnitPrice), quoteCurrency, forexContext)
        val todayBaseValue = when {
            holding.todayCostPrice != null -> {
                convertAmountToCny(multiplyOrNull(quantity, holding.todayCostPrice), holdingCurrency, forexContext)
            }

            derivedPreviousUnitPrice != null -> {
                convertAmountToCny(multiplyOrNull(quantity, derivedPreviousUnitPrice), quoteCurrency, forexContext)
            }

            else -> {
                convertAmountToCny(multiplyOrNull(quantity, configuredBaseUnitPrice), holdingCurrency, forexContext)
            }
        }
        val totalProfit = subtractOrNull(currentValue, costAmount)
        val todayProfit = subtractOrNull(currentValue, todayBaseValue)

        return PositionProfitSnapshot(
            holdingId = holding.id,
            assetType = AssetType.CRYPTO,
            code = holding.code,
            displayName = holding.displayName,
            quantity = quantity,
            costAmount = costAmount,
            currentValue = currentValue,
            totalProfit = totalProfit,
            totalProfitPercent = ratio(totalProfit, costAmount),
            todayProfit = todayProfit,
            todayProfitPercent = ratio(todayProfit, todayBaseValue),
            quoteAvailable = currentValue != null,
        )
    }

    private fun summarize(
        assetType: AssetType,
        items: List<PositionProfitSnapshot>,
    ): AssetProfitSummary {
        val availableItems = items.filter { it.quoteAvailable }
        val totalCost = availableItems.sumOfAmounts { it.costAmount }
        val currentValue = availableItems.sumOfAmounts { it.currentValue }
        val totalProfit = availableItems.sumOfAmounts { it.totalProfit }
        val todayProfit = availableItems.sumOfAmounts { it.todayProfit }
        val previousValue = currentValue.subtract(todayProfit)

        return AssetProfitSummary(
            assetType = assetType,
            totalCost = totalCost,
            currentValue = currentValue,
            totalProfit = totalProfit,
            totalProfitPercent = ratio(totalProfit, totalCost),
            todayProfit = todayProfit,
            todayProfitPercent = ratio(todayProfit, previousValue),
            items = items,
        )
    }

    private fun resolveCostAmount(holding: HoldingConfig): BigDecimal? {
        return holding.investedAmount ?: multiplyOrNull(holding.quantity, holding.costPrice)
    }

    private fun convertAmountToCny(
        amount: BigDecimal?,
        currency: String?,
        forexContext: ForexConversionContext,
    ): BigDecimal? {
        if (amount == null) {
            return null
        }
        val normalizedCurrency = normalizeCurrency(currency, forexContext) ?: return amount
        if (normalizedCurrency == DEFAULT_CURRENCY) {
            return amount
        }

        val ratePerUnit = forexContext.ratePerUnitByCurrency[normalizedCurrency] ?: return amount
        return amount.multiply(ratePerUnit).stripTrailingZeros()
    }

    private fun multiplyOrNull(
        left: BigDecimal?,
        right: BigDecimal?,
    ): BigDecimal? {
        if (left == null || right == null) {
            return null
        }
        return left.multiply(right)
    }

    private fun subtractOrNull(
        left: BigDecimal?,
        right: BigDecimal?,
    ): BigDecimal? {
        if (left == null || right == null) {
            return null
        }
        return left.subtract(right)
    }

    private fun ratio(
        numerator: BigDecimal?,
        denominator: BigDecimal?,
    ): BigDecimal? {
        if (numerator == null || denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return null
        }
        return numerator
            .divide(denominator, 6, RoundingMode.HALF_UP)
            .multiply(HUNDRED)
            .stripTrailingZeros()
    }

    private fun deriveCryptoPreviousPrice(quote: CryptoQuote?): BigDecimal? {
        if (quote?.currentPrice == null || quote.priceChangePercentage24h == null) {
            return null
        }
        val denominator = BigDecimal.ONE.add(
            quote.priceChangePercentage24h
                .divide(HUNDRED, 6, RoundingMode.HALF_UP)
        )
        if (denominator.compareTo(BigDecimal.ZERO) == 0) {
            return null
        }
        return quote.currentPrice
            .divide(denominator, 8, RoundingMode.HALF_UP)
            .stripTrailingZeros()
    }

    private fun List<PositionProfitSnapshot>.sumOfAmounts(selector: (PositionProfitSnapshot) -> BigDecimal?): BigDecimal {
        return fold(BigDecimal.ZERO) { total, item ->
            total + (selector(item) ?: BigDecimal.ZERO)
        }
    }

    private fun buildForexContext(forexRates: List<ForexRate>): ForexConversionContext {
        val aliasMap = linkedMapOf<String, String>()
        aliasMap.putAll(DEFAULT_FOREX_CURRENCY_ALIASES)
        val rateMap = linkedMapOf<String, BigDecimal>()

        forexRates.forEach { rate ->
            // BOC quotes are stored as CNY per 100 units for most currencies. Normalize them to a
            // per-unit multiplier once here so every profit branch can stay currency-agnostic.
            val baseCurrency = extractForexBaseCurrency(rate.currencyCode)
                ?: normalizeDefaultCurrencyAlias(rate.currencyName)
                ?: return@forEach
            val ratePerUnit = rate.toRatePerUnit() ?: return@forEach
            rateMap[baseCurrency] = ratePerUnit
            aliasMap[rate.currencyName.trim()] = baseCurrency
            aliasMap[baseCurrency] = baseCurrency
            aliasMap[rate.currencyCode.uppercase()] = baseCurrency
        }

        return ForexConversionContext(
            ratePerUnitByCurrency = rateMap,
            currencyAliases = aliasMap,
        )
    }

    private fun extractForexBaseCurrency(currencyPair: String?): String? {
        if (currencyPair.isNullOrBlank()) {
            return null
        }
        return currencyPair.substringBefore("/").trim().uppercase().takeIf { it.isNotEmpty() }
    }

    private fun resolveStockQuoteCurrency(
        holding: HoldingConfig,
        quote: StockQuote?,
        forexContext: ForexConversionContext,
    ): String {
        return when (quote?.exchange) {
            StockExchange.SSE,
            StockExchange.SZSE,
            StockExchange.BSE,
            null,
            -> DEFAULT_CURRENCY

            StockExchange.HKEX -> "HKD"
            StockExchange.NASDAQ,
            StockExchange.NYSE,
            -> "USD"

            StockExchange.OTHER -> normalizeCurrency(holding.currency, forexContext) ?: DEFAULT_CURRENCY
        }
    }

    private fun normalizeCurrency(
        rawCurrency: String?,
        forexContext: ForexConversionContext,
    ): String? {
        val normalized = rawCurrency?.trim().orEmpty()
        if (normalized.isEmpty()) {
            return null
        }

        val upper = normalized.uppercase()
        return forexContext.currencyAliases[normalized]
            ?: forexContext.currencyAliases[upper]
            ?: normalizeDefaultCurrencyAlias(normalized)
            ?: upper.takeIf { it.matches(ISO_CURRENCY_PATTERN) }
    }

    private fun normalizeDefaultCurrencyAlias(rawCurrency: String): String? {
        val normalized = rawCurrency.trim()
        if (normalized.isEmpty()) {
            return null
        }
        return DEFAULT_FOREX_CURRENCY_ALIASES[normalized]
            ?: DEFAULT_FOREX_CURRENCY_ALIASES[normalized.uppercase()]
    }

    private fun ForexRate.toRatePerUnit(): BigDecimal? {
        val quotedPerHundred = conversionPrice ?: spotBuyPrice ?: spotSellPrice ?: cashBuyPrice ?: cashSellPrice
        return quotedPerHundred
            ?.divide(HUNDRED, 8, RoundingMode.HALF_UP)
            ?.stripTrailingZeros()
    }

    companion object {
        private val HUNDRED = BigDecimal("100")
        private const val DEFAULT_CURRENCY = "CNY"
        private val ISO_CURRENCY_PATTERN = Regex("""[A-Z]{3}""")
        private val DEFAULT_FOREX_CURRENCY_ALIASES = mapOf(
            "人民币" to "CNY",
            "RMB" to "CNY",
            "CNY" to "CNY",
            "美元" to "USD",
            "USD" to "USD",
            "港币" to "HKD",
            "HKD" to "HKD",
            "澳门元" to "MOP",
            "MOP" to "MOP",
            "欧元" to "EUR",
            "EUR" to "EUR",
            "英镑" to "GBP",
            "GBP" to "GBP",
            "日元" to "JPY",
            "JPY" to "JPY",
            "韩国元" to "KRW",
            "KRW" to "KRW",
            "新台币" to "TWD",
            "TWD" to "TWD",
            "新加坡元" to "SGD",
            "SGD" to "SGD",
            "加拿大元" to "CAD",
            "CAD" to "CAD",
            "澳大利亚元" to "AUD",
            "AUD" to "AUD",
        )
    }
}

private data class ForexConversionContext(
    val ratePerUnitByCurrency: Map<String, BigDecimal>,
    val currencyAliases: Map<String, String>,
)
