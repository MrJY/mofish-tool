package online.mofish.tool.data

import online.mofish.tool.data.crypto.CryptoQuoteClient
import online.mofish.tool.data.forex.BocForexClient
import online.mofish.tool.data.fund.FundQuoteClient
import online.mofish.tool.data.stock.StockQuoteClient
import online.mofish.tool.domain.MoFishWorkspace
import online.mofish.tool.domain.MoFishRefreshModule
import online.mofish.tool.domain.StockQuote
import online.mofish.tool.settings.MoFishSettingsState

class RemoteMoFishDataSource(
    private val fallbackDataSource: MoFishDataSource = StaticMoFishDataSource(),
    private val cryptoQuoteClient: CryptoQuoteClient = CryptoQuoteClient(),
    private val bocForexClient: BocForexClient = BocForexClient(),
    private val fundQuoteClient: FundQuoteClient = FundQuoteClient(),
    private val stockQuoteClient: StockQuoteClient = StockQuoteClient(),
    private val marketIndexQuoteClient: StockQuoteClient = StockQuoteClient(),
) : MoFishDataSource {
    /**
     * 创建一个轻量级工作区骨架，用于界面首次打开时先展示占位状态。
     * @param projectName 当前 IntelliJ 项目的名称，用于区分不同项目的缓存、状态和刷新任务。
     * @param settings 当前mofish工具设置快照，提供关注列表、持仓、提醒和刷新配置。
     * @return 处理后的结果或当前状态。
     */
    override fun createSkeletonWorkspace(
        projectName: String,
        settings: MoFishSettingsState,
    ): MoFishWorkspace = fallbackDataSource.createSkeletonWorkspace(projectName, settings)

    /**
     * 根据项目名称和当前设置加载完整工作区数据。
     * @param projectName 当前 IntelliJ 项目的名称，用于区分不同项目的缓存、状态和刷新任务。
     * @param settings 当前mofish工具设置快照，提供关注列表、持仓、提醒和刷新配置。
     * @return 处理后的结果或当前状态。
     */
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
        val liveIndexQuotes = runCatching {
            fetchIndexQuotes(fallbackWorkspace.indexQuotes)
        }
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

    /**
     * 只刷新指定模块的数据，并把结果合并回当前工作区。
     * @param projectName 当前 IntelliJ 项目的名称，用于区分不同项目的缓存、状态和刷新任务。
     * @param settings 当前mofish工具设置快照，提供关注列表、持仓、提醒和刷新配置。
     * @param currentWorkspace 刷新前已有的工作区数据，用于保留未刷新模块的内容。
     * @param modules 需要刷新或处理的业务模块集合。
     * @return 处理后的结果或当前状态。
     */
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
            val liveIndexQuotes = runCatching {
                fetchIndexQuotes(fallbackWorkspace.indexQuotes)
            }
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

        return nextWorkspace
    }

    private fun fetchIndexQuotes(fallbackQuotes: List<StockQuote>): List<StockQuote> {
        val fetchedQuotes = marketIndexQuoteClient.fetchQuotes(fallbackQuotes.map { it.code })
        val quoteByCode = fetchedQuotes.associateBy { it.code.lowercase() }
        return fallbackQuotes.map { fallbackQuote ->
            quoteByCode[fallbackQuote.code.lowercase()] ?: fallbackQuote
        }
    }
}
