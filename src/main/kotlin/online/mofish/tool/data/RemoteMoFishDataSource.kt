package online.mofish.tool.data

import online.mofish.tool.data.crypto.CryptoQuoteClient
import online.mofish.tool.data.forex.BocForexClient
import online.mofish.tool.data.fund.FundQuoteClient
import online.mofish.tool.data.index.defaultMarketIndexCodes
import online.mofish.tool.data.stock.StockQuoteClient
import online.mofish.tool.domain.MoFishWorkspace
import online.mofish.tool.domain.MoFishRefreshModule
import online.mofish.tool.settings.MoFishSettingsState

class RemoteMoFishDataSource(
    private val fallbackDataSource: MoFishDataSource = StaticMoFishDataSource(),
    private val cryptoQuoteClient: CryptoQuoteClient = CryptoQuoteClient(),
    private val bocForexClient: BocForexClient = BocForexClient(),
    private val fundQuoteClient: FundQuoteClient = FundQuoteClient(),
    private val stockQuoteClient: StockQuoteClient = StockQuoteClient(),
    private val marketIndexQuoteClient: StockQuoteClient = StockQuoteClient(),
    private val marketIndexCodes: List<String> = defaultMarketIndexCodes(),
) : MoFishDataSource {
    override fun createSkeletonWorkspace(
        projectName: String,
        settings: MoFishSettingsState,
    ): MoFishWorkspace = fallbackDataSource.createSkeletonWorkspace(projectName, settings)

    override fun loadWorkspace(
        projectName: String,
        settings: MoFishSettingsState,
    ): MoFishWorkspace {
        // Always build the workspace shape from settings first. Remote calls then enrich each
        // module independently, so a single broken provider does not blank out the whole tool.
        val fallbackWorkspace = fallbackDataSource.loadWorkspace(projectName, settings)
        val liveFundQuotes = fallbackWorkspace.fundQuotes.map { fallbackQuote ->
            runCatching { fundQuoteClient.fetchQuote(fallbackQuote.code) }
                .getOrElse { fallbackQuote }
        }
        val liveStockQuotes = runCatching {
            val fetchedQuotes = stockQuoteClient.fetchQuotes(fallbackWorkspace.stockQuotes.map { it.code })
            val quoteByCode = fetchedQuotes.associateBy { it.code }
            fallbackWorkspace.stockQuotes.map { fallbackQuote ->
                quoteByCode[fallbackQuote.code] ?: fallbackQuote
            }
        }.getOrElse { fallbackWorkspace.stockQuotes }
        val liveCryptoQuotes = runCatching {
            val fetchedQuotes = cryptoQuoteClient.fetchQuotes(fallbackWorkspace.cryptoQuotes.map { it.code })
            val quoteByCode = fetchedQuotes.associateBy { it.code }
            fallbackWorkspace.cryptoQuotes.map { fallbackQuote ->
                quoteByCode[fallbackQuote.code] ?: fallbackQuote
            }
        }.getOrElse { fallbackWorkspace.cryptoQuotes }
        val liveIndexQuotes = runCatching { marketIndexQuoteClient.fetchQuotes(marketIndexCodes) }
            .map { quotes -> quotes.ifEmpty { fallbackWorkspace.indexQuotes } }
            .getOrElse { fallbackWorkspace.indexQuotes }
        val liveForexRates = runCatching { bocForexClient.fetchRates() }
            .map { rates -> rates.ifEmpty { fallbackWorkspace.forexRates } }
            .getOrElse { fallbackWorkspace.forexRates }
        return fallbackWorkspace.copy(
            fundQuotes = liveFundQuotes,
            stockQuotes = liveStockQuotes,
            cryptoQuotes = liveCryptoQuotes,
            forexRates = liveForexRates,
            indexQuotes = liveIndexQuotes,
        )
    }

    override fun loadWorkspaceModules(
        projectName: String,
        settings: MoFishSettingsState,
        currentWorkspace: MoFishWorkspace,
        modules: Set<MoFishRefreshModule>,
    ): MoFishWorkspace {
        if (modules.isEmpty()) {
            return currentWorkspace
        }

        val fallbackWorkspace = fallbackDataSource.loadWorkspace(projectName, settings)
        var nextWorkspace = currentWorkspace.copy(
            holdings = settings.holdings,
            reminderRules = settings.reminders,
            aiConfig = settings.aiConfig,
        )

        if (MoFishRefreshModule.FUNDS in modules) {
            val fallbackFundQuotes = fallbackWorkspace.fundQuotes
            val liveFundQuotes = fallbackFundQuotes.map { fallbackQuote ->
                runCatching { fundQuoteClient.fetchQuote(fallbackQuote.code) }
                    .getOrElse { fallbackQuote }
            }
            nextWorkspace = nextWorkspace.copy(fundQuotes = liveFundQuotes)
        }

        if (MoFishRefreshModule.STOCKS in modules) {
            val fallbackStockQuotes = fallbackWorkspace.stockQuotes
            val liveStockQuotes = runCatching {
                val fetchedQuotes = stockQuoteClient.fetchQuotes(fallbackStockQuotes.map { it.code })
                val quoteByCode = fetchedQuotes.associateBy { it.code }
                fallbackStockQuotes.map { fallbackQuote ->
                    quoteByCode[fallbackQuote.code] ?: fallbackQuote
                }
            }.getOrElse { fallbackStockQuotes }
            nextWorkspace = nextWorkspace.copy(stockQuotes = liveStockQuotes)
        }

        if (MoFishRefreshModule.CRYPTO in modules) {
            val fallbackCryptoQuotes = fallbackWorkspace.cryptoQuotes
            val liveCryptoQuotes = runCatching {
                val fetchedQuotes = cryptoQuoteClient.fetchQuotes(fallbackCryptoQuotes.map { it.code })
                val quoteByCode = fetchedQuotes.associateBy { it.code }
                fallbackCryptoQuotes.map { fallbackQuote ->
                    quoteByCode[fallbackQuote.code] ?: fallbackQuote
                }
            }.getOrElse { fallbackCryptoQuotes }
            nextWorkspace = nextWorkspace.copy(cryptoQuotes = liveCryptoQuotes)
        }

        if (MoFishRefreshModule.INDICES in modules) {
            val liveIndexQuotes = runCatching { marketIndexQuoteClient.fetchQuotes(marketIndexCodes) }
                .map { quotes -> quotes.ifEmpty { fallbackWorkspace.indexQuotes } }
                .getOrElse { fallbackWorkspace.indexQuotes }
            nextWorkspace = nextWorkspace.copy(indexQuotes = liveIndexQuotes)
        }

        if (MoFishRefreshModule.FOREX in modules) {
            val liveForexRates = runCatching { bocForexClient.fetchRates() }
                .map { rates -> rates.ifEmpty { fallbackWorkspace.forexRates } }
                .getOrElse { fallbackWorkspace.forexRates }
            nextWorkspace = nextWorkspace.copy(forexRates = liveForexRates)
        }

        if (MoFishRefreshModule.NEWS in modules) {
            nextWorkspace = nextWorkspace.copy(flashNews = fallbackWorkspace.flashNews)
        }

        return nextWorkspace
    }
}
