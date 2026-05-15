package online.mofish.tool.settings

import online.mofish.tool.domain.AiConfig
import online.mofish.tool.domain.AiStockHistoryRange
import online.mofish.tool.domain.AssetType
import online.mofish.tool.domain.HoldingConfig
import online.mofish.tool.domain.ReminderDirection
import online.mofish.tool.domain.ReminderMetric
import online.mofish.tool.domain.ReminderRule
import java.math.BigDecimal

enum class MoFishSortDirection(
    private val displayName: String,
) {
    ASC("升序"),
    DESC("降序"),
    ;

    override fun toString(): String = displayName
}

enum class MoFishQuoteSortField(
    private val displayName: String,
) {
    DISPLAY_NAME("名称"),
    DAILY_CHANGE_PERCENT("日涨跌幅"),
    UPDATED_AT("更新时间"),
    ;

    override fun toString(): String = displayName
}

enum class MoFishReminderSortField(
    private val displayName: String,
) {
    DISPLAY_NAME("名称"),
    THRESHOLD("阈值"),
    ;

    override fun toString(): String = displayName
}

data class MoFishWatchlistSettings(
    val fundCodes: List<String> = listOf("161725"),
    val stockCodes: List<String> = listOf("sz300750"),
    val cryptoIds: List<String> = listOf("bitcoin"),
)

data class MoFishSortSettings(
    val quoteField: MoFishQuoteSortField = MoFishQuoteSortField.DAILY_CHANGE_PERCENT,
    val quoteDirection: MoFishSortDirection = MoFishSortDirection.DESC,
    val reminderField: MoFishReminderSortField = MoFishReminderSortField.DISPLAY_NAME,
    val reminderDirection: MoFishSortDirection = MoFishSortDirection.ASC,
)

data class MoFishRefreshSettings(
    val intervalSeconds: Int = 300,
    val autoRefreshEnabled: Boolean = true,
    val autoRefreshStartMinuteOfDay: Int = 9 * 60 + 30,
    val autoRefreshEndMinuteOfDay: Int = 15 * 60,
    val openToolWindowOnStartup: Boolean = false,
)

data class MoFishSettingsState(
    val watchlist: MoFishWatchlistSettings = MoFishWatchlistSettings(),
    val holdings: List<HoldingConfig> = defaultHoldings(),
    val reminders: List<ReminderRule> = defaultReminders(),
    val aiConfig: AiConfig = AiConfig(
        apiKey = "",
        baseUrl = "https://api.openai.com/v1",
        model = "gpt-4.1-mini",
        stockHistoryRange = AiStockHistoryRange.THREE_MONTHS,
    ),
    val sortSettings: MoFishSortSettings = MoFishSortSettings(),
    val refresh: MoFishRefreshSettings = MoFishRefreshSettings(),
    val showStatusBarWidget: Boolean = true,
    val showHoldingProfit: Boolean = true,
) {
    val refreshIntervalSeconds: Int
        get() = refresh.intervalSeconds

    val autoRefreshWindowText: String
        get() = "${formatMinuteOfDay(refresh.autoRefreshStartMinuteOfDay)} - ${formatMinuteOfDay(refresh.autoRefreshEndMinuteOfDay)}"

    val openToolWindowOnStartup: Boolean
        get() = refresh.openToolWindowOnStartup

    companion object {
        private fun defaultHoldings(): List<HoldingConfig> {
            return listOf(
                HoldingConfig(
                    id = "fund:161725",
                    assetType = AssetType.FUND,
                    code = "161725",
                    displayName = "招商中证白酒指数(LOF)A",
                    investedAmount = decimal("12000.00"),
                    quantity = decimal("14560.12"),
                    costPrice = decimal("0.8242"),
                ),
                HoldingConfig(
                    id = "stock:sz300750",
                    assetType = AssetType.STOCK,
                    code = "sz300750",
                    displayName = "宁德时代",
                    quantity = decimal("100"),
                    costPrice = decimal("198.50"),
                    todayCostPrice = decimal("209.10"),
                ),
                HoldingConfig(
                    id = "crypto:bitcoin",
                    assetType = AssetType.CRYPTO,
                    code = "bitcoin",
                    displayName = "Bitcoin",
                    quantity = decimal("0.05"),
                    costPrice = decimal("62000.00"),
                    currency = "USD",
                ),
            )
        }

        private fun defaultReminders(): List<ReminderRule> {
            return listOf(
                ReminderRule(
                    id = "rule-price-up",
                    assetType = AssetType.STOCK,
                    code = "sz300750",
                    displayName = "宁德时代",
                    metric = ReminderMetric.PRICE,
                    direction = ReminderDirection.ABOVE,
                    threshold = decimal("220.00"),
                ),
                ReminderRule(
                    id = "rule-fund-drop",
                    assetType = AssetType.FUND,
                    code = "161725",
                    displayName = "招商中证白酒指数(LOF)A",
                    metric = ReminderMetric.CHANGE_PERCENT,
                    direction = ReminderDirection.BELOW,
                    threshold = decimal("1.50"),
                ),
                ReminderRule(
                    id = "rule-crypto-price-up",
                    assetType = AssetType.CRYPTO,
                    code = "bitcoin",
                    displayName = "Bitcoin",
                    metric = ReminderMetric.PRICE,
                    direction = ReminderDirection.ABOVE,
                    threshold = decimal("70000"),
                ),
            )
        }

        private fun decimal(value: String): BigDecimal = BigDecimal(value)
    }
}

private const val MINUTES_PER_DAY = 24 * 60

internal fun normalizeMinuteOfDay(value: Int): Int = Math.floorMod(value, MINUTES_PER_DAY)

internal fun minuteOfDay(hour: Int, minute: Int): Int {
    val normalizedHour = hour.coerceIn(0, 23)
    val normalizedMinute = minute.coerceIn(0, 59)
    return normalizedHour * 60 + normalizedMinute
}

internal fun formatMinuteOfDay(value: Int): String {
    val normalized = normalizeMinuteOfDay(value)
    val hour = normalized / 60
    val minute = normalized % 60
    return "%02d:%02d".format(hour, minute)
}
