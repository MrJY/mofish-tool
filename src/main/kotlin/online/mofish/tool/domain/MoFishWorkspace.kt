package online.mofish.tool.domain

data class MoFishWorkspace(
    /** 当前 IntelliJ 项目名称，用于隔离不同项目的关注列表和缓存。 */
    val projectName: String,
    /** 当前工作区的基金行情列表。 */
    val fundQuotes: List<FundQuote>,
    /** 当前工作区的股票行情列表。 */
    val stockQuotes: List<StockQuote>,
    /** 当前工作区的虚拟币行情列表。 */
    val cryptoQuotes: List<CryptoQuote> = emptyList(),
    /** 用户配置的持仓列表。 */
    val holdings: List<HoldingConfig>,
    /** 用户配置的价格或涨跌幅提醒规则列表。 */
    val reminderRules: List<ReminderRule>,
    /** AI 功能配置。 */
    val aiConfig: AiConfig,
    /** 当前工作区展示或计算使用的外汇牌价列表。 */
    val forexRates: List<ForexRate>,
    /** 当前工作区的市场指数行情列表。 */
    val indexQuotes: List<StockQuote> = emptyList(),
) {
    /** 尚未清仓且有数量或金额的有效持仓数量。 */
    val activeHoldingCount: Int
        get() = holdings.count { it.hasPosition }

    /** 当前启用中的提醒规则数量。 */
    val enabledReminderCount: Int
        get() = reminderRules.count { it.enabled }
}
