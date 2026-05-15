package online.mofish.tool.domain

data class MoFishWorkspace(
    val projectName: String,
    val fundQuotes: List<FundQuote>,
    val stockQuotes: List<StockQuote>,
    val cryptoQuotes: List<CryptoQuote> = emptyList(),
    val holdings: List<HoldingConfig>,
    val reminderRules: List<ReminderRule>,
    val aiConfig: AiConfig,
    val flashNews: List<FlashNewsItem>,
    val forexRates: List<ForexRate>,
    val indexQuotes: List<StockQuote> = emptyList(),
) {
    val activeHoldingCount: Int
        get() = holdings.count { it.hasPosition }

    val enabledReminderCount: Int
        get() = reminderRules.count { it.enabled }
}
