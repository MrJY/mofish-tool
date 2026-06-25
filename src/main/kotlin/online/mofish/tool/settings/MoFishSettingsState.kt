package online.mofish.tool.settings

import online.mofish.tool.data.index.defaultMarketIndexCodes
import online.mofish.tool.domain.HoldingConfig
import online.mofish.tool.domain.MoFishRefreshModule
import online.mofish.tool.domain.ReminderRule

enum class MoFishSortDirection(
    private val displayName: String,
) {
    ASC("升序"),
    DESC("降序"),
    ;

    override fun toString(): String = displayName
}

enum class MoFishStockTableColumn(
    private val displayName: String,
) {
    CODE("代码"),
    NAME("名称"),
    CURRENT_PRICE("现价"),
    CHANGE_PERCENT("涨跌幅"),
    OPEN_PRICE("开盘"),
    PREVIOUS_CLOSE("昨收"),
    VOLUME("成交量"),
    TURNOVER("成交额"),
    TOTAL_PROFIT("持仓收益"),
    ;

    /**
     * 转换为String表示。
     * @return 处理后的结果或当前状态。
     */
    override fun toString(): String = displayName

    companion object {
        val defaultColumns: Set<MoFishStockTableColumn> = setOf(
            CODE,
            NAME,
            CURRENT_PRICE,
            CHANGE_PERCENT,
        )
    }
}

data class MoFishWatchlistSettings(
    val fundCodes: List<String> = emptyList(),
    val stockCodes: List<String> = emptyList(),
    val indexCodes: List<String> = defaultMarketIndexCodes(),
    val stockGroups: List<String> = emptyList(),
    val stockGroupAssignments: Map<String, String> = emptyMap(),
    val cryptoIds: List<String> = listOf("bitcoin"),
) {
    /**
     * 规范化d股票Groups，统一后续处理使用的表示形式。
     * @return 处理后的结果或当前状态。
     */
    fun normalizedStockGroups(): List<String> {
        return stockGroups
            .map(::normalizeStockGroupValue)
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
    }

    /**
     * 处理 groupForStock 相关逻辑，并返回调用方需要的结果。
     * @param code 资产代码或业务标识。
     * @return 处理后的结果或当前状态。
     */
    fun groupForStock(code: String): String? {
        val normalizedCode = code.trim().lowercase()
        val assignedGroup = stockGroupAssignments[normalizedCode] ?: stockGroupAssignments.entries
            .firstOrNull { it.key.equals(normalizedCode, ignoreCase = true) }
            ?.value
        val groups = normalizedStockGroups()
        return assignedGroup
            ?.let(::normalizeStockGroupValue)
            ?.takeIf { group -> groups.any { it.equals(group, ignoreCase = true) } }
    }
}

/**
 * 规范化股票Group值，统一后续处理使用的表示形式。
 * @param rawGroupName rawGroup名称。
 * @return 处理后的结果或当前状态。
 */
internal fun normalizeStockGroupValue(rawGroupName: String): String = rawGroupName.trim()

data class MoFishSortSettings(
    val quoteDirection: MoFishSortDirection = MoFishSortDirection.DESC,
)

data class MoFishModuleRefreshSettings(
    val enabled: Boolean = true,
    val intervalSeconds: Int = 10,
    val startMinuteOfDay: Int = 9 * 60 + 30,
    val endMinuteOfDay: Int = 15 * 60,
)

data class MoFishRefreshSettings(
    val intervalSeconds: Int = 10,
    val autoRefreshEnabled: Boolean = true,
    val autoRefreshStartMinuteOfDay: Int = 9 * 60 + 30,
    val autoRefreshEndMinuteOfDay: Int = 15 * 60,
    val autoRefreshModules: Set<MoFishRefreshModule> = MoFishRefreshModule.defaultAutoRefreshModules,
    val moduleSettings: Map<MoFishRefreshModule, MoFishModuleRefreshSettings> = emptyMap(),
    val openToolWindowOnStartup: Boolean = false,
) {
    fun settingsFor(module: MoFishRefreshModule): MoFishModuleRefreshSettings {
        val settings = moduleSettings[module] ?: MoFishModuleRefreshSettings(
            enabled = autoRefreshEnabled && module in autoRefreshModules,
            intervalSeconds = intervalSeconds,
            startMinuteOfDay = autoRefreshStartMinuteOfDay,
            endMinuteOfDay = autoRefreshEndMinuteOfDay,
        )
        return if (module in MoFishRefreshModule.autoRefreshModules) {
            settings
        } else {
            settings.copy(enabled = false)
        }
    }

    fun effectiveModuleSettings(): Map<MoFishRefreshModule, MoFishModuleRefreshSettings> {
        return MoFishRefreshModule.autoRefreshModules.associateWith(::settingsFor)
    }
}

data class MoFishUiSettings(
    val stockTableColumns: Set<MoFishStockTableColumn> = MoFishStockTableColumn.defaultColumns,
    val enabledModules: Set<MoFishRefreshModule> = MoFishRefreshModule.defaultEnabledModules,
)

data class MoFishStatusBarSettings(
    val enabledModules: Set<MoFishRefreshModule> = MoFishRefreshModule.visibleModules,
    val rotationIntervalSeconds: Int = 3,
)

data class MoFishSettingsState(
    val watchlist: MoFishWatchlistSettings = MoFishWatchlistSettings(),
    val holdings: List<HoldingConfig> = emptyList(),
    val reminders: List<ReminderRule> = emptyList(),
    val sortSettings: MoFishSortSettings = MoFishSortSettings(),
    val refresh: MoFishRefreshSettings = MoFishRefreshSettings(),
    val ui: MoFishUiSettings = MoFishUiSettings(),
    val statusBar: MoFishStatusBarSettings = MoFishStatusBarSettings(),
    val showStatusBarWidget: Boolean = true,
    val showHoldingProfit: Boolean = false,
) {
    val refreshIntervalSeconds: Int
        get() = refresh.intervalSeconds

    val autoRefreshWindowText: String
        get() = "${formatMinuteOfDay(refresh.autoRefreshStartMinuteOfDay)} - ${formatMinuteOfDay(refresh.autoRefreshEndMinuteOfDay)}"

    val openToolWindowOnStartup: Boolean
        get() = refresh.openToolWindowOnStartup

}

private const val MINUTES_PER_DAY = 24 * 60

/**
 * 规范化MinuteOfDay，统一后续处理使用的表示形式。
 * @param value 待解析、格式化或写入的原始值。
 * @return 处理后的结果或当前状态。
 */
internal fun normalizeMinuteOfDay(value: Int): Int = Math.floorMod(value, MINUTES_PER_DAY)

/**
 * 处理 minuteOfDay 相关逻辑，并返回调用方需要的结果。
 * @param hour hour。
 * @param minute minute。
 * @return 处理后的结果或当前状态。
 */
internal fun minuteOfDay(hour: Int, minute: Int): Int {
    val normalizedHour = hour.coerceIn(0, 23)
    val normalizedMinute = minute.coerceIn(0, 59)
    return normalizedHour * 60 + normalizedMinute
}

/**
 * 格式化MinuteOfDay，用于界面展示。
 * @param value 待解析、格式化或写入的原始值。
 * @return 处理后的结果或当前状态。
 */
internal fun formatMinuteOfDay(value: Int): String {
    val normalized = normalizeMinuteOfDay(value)
    val hour = normalized / 60
    val minute = normalized % 60
    return "%02d:%02d".format(hour, minute)
}
