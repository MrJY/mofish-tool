package online.mofish.tool.data

import online.mofish.tool.data.crypto.CryptoQuoteClient
import online.mofish.tool.data.forex.BocForexClient
import online.mofish.tool.data.fund.FundQuoteClient
import online.mofish.tool.data.index.defaultMarketIndexCodes
import online.mofish.tool.data.stock.StockQuoteClient
import online.mofish.tool.domain.MoFishWorkspace
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
}
