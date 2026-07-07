package online.mofish.tool.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import online.mofish.tool.data.crypto.CryptoQuoteClient
import online.mofish.tool.data.forex.BocForexClient
import online.mofish.tool.data.forex.buildForexCurrencyPairCode
import online.mofish.tool.data.fund.FundQuoteClient
import online.mofish.tool.data.stock.StockQuoteClient
import online.mofish.tool.domain.AssetType
import online.mofish.tool.domain.CryptoSearchSuggestion
import online.mofish.tool.domain.ForexRate
import online.mofish.tool.domain.FundSearchSuggestion
import online.mofish.tool.domain.HoldingConfig
import online.mofish.tool.domain.MoFishRefreshModule
import online.mofish.tool.domain.ReminderRule
import online.mofish.tool.domain.StockSearchSuggestion
import online.mofish.tool.settings.MoFishSortDirection
import online.mofish.tool.settings.MoFishSettingsService
import online.mofish.tool.settings.MoFishSettingsState
import online.mofish.tool.settings.MoFishSortSettings
import online.mofish.tool.settings.MoFishWatchlistSettings
import online.mofish.tool.settings.normalizeStockGroupValue
import online.mofish.tool.state.MoFishProjectEvent
import online.mofish.tool.state.MoFishWatchlistState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

@Service(Service.Level.PROJECT)
class MoFishWatchlistService(
    private val project: Project,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val projectService = project.service<MoFishProjectService>()
    private val appService = service<MoFishAppService>()
    private val cacheService = service<MoFishMemoryCacheService>()
    private val settingsService = service<MoFishSettingsService>()
    private val refreshSchedulerService = service<MoFishRefreshSchedulerService>()
    private val reminderService = project.service<MoFishReminderService>()
    private val bocForexClient = BocForexClient()
    private val cryptoQuoteClient = CryptoQuoteClient()
    private val fundQuoteClient = FundQuoteClient()
    private val stockQuoteClient = StockQuoteClient()
    private val profitCalculator = MoFishProfitCalculator()
    private val stateFlow = MutableStateFlow<MoFishWatchlistState?>(null)

    @Volatile
    private var activated = false
    private var lastAutoRefreshAtByModule: Map<MoFishRefreshModule, Instant> = emptyMap()

    val states: StateFlow<MoFishWatchlistState?> = stateFlow.asStateFlow()
    val events: SharedFlow<MoFishProjectEvent> = projectService.events

    init {
        scope.launch {
            combine(
                projectService.states.filterNotNull(),
                settingsService.states,
                appService.states,
                cacheService.states,
                refreshSchedulerService.states,
            ) { projectState, settingsState, appState, cacheState, schedulerState ->
                MoFishWatchlistState(
                    projectState = projectState,
                    settingsState = settingsState,
                    appState = appState,
                    cacheState = cacheState,
                    schedulerState = schedulerState,
                    profitSnapshot = profitCalculator.calculate(projectState.workspace),
                )
            }.collect { state ->
                reminderService.notifyIfNeeded(stateFlow.value, state)
                stateFlow.value = state
            }
        }
    }

    /**
     * 处理 activate 相关逻辑，并返回调用方需要的结果。
     */
    fun activate() {
        if (activated) {
            return
        }
        activated = true
        projectService.ensureState(project.name)
        refreshSchedulerService.registerProject(project.name) { force ->
            refreshConfiguredModules(force = force)
        }
    }

    /**
     * 处理 deactivate 相关逻辑，并返回调用方需要的结果。
     */
    fun deactivate() {
        if (!activated) {
            return
        }
        activated = false
        refreshSchedulerService.unregisterProject(project.name)
    }

    /**
     * 释放服务持有的后台任务和运行资源。
     */
    fun dispose() {
        deactivate()
        scope.cancel()
    }

    /**
     * 处理 refresh 相关逻辑，并返回调用方需要的结果。
     * @param force 是否跳过缓存并强制重新读取数据。
     * @return 处理后的结果或当前状态。
     */
    fun refresh(force: Boolean = false) {
        scope.launch(Dispatchers.IO) {
            projectService.refreshState(project.name, force = force)
        }
    }

    /**
     * 处理 refreshModule 相关逻辑，并返回调用方需要的结果。
     * @param module 模块。
     * @param force 是否跳过缓存并强制重新读取数据。
     * @return 处理后的结果或当前状态。
     */
    fun refreshModule(module: MoFishRefreshModule, force: Boolean = true) {
        refreshModules(setOf(module), force)
    }

    /**
     * 刷新指定的一组业务模块，并保留其他模块已有数据。
     * @param modules 需要刷新或处理的业务模块集合。
     * @param force 是否跳过缓存并强制重新读取数据。
     * @return 处理后的结果或当前状态。
     */
    fun refreshModules(modules: Set<MoFishRefreshModule>, force: Boolean = true) {
        scope.launch(Dispatchers.IO) {
            projectService.refreshModules(project.name, modules, force = force)
        }
    }

    /**
     * 处理 refreshConfiguredModules 相关逻辑，并返回调用方需要的结果。
     * @param force 是否跳过缓存并强制重新读取数据。
     * @return 处理后的结果或当前状态。
     */
    private fun refreshConfiguredModules(force: Boolean = true) {
        val now = Instant.now()
        val currentMinuteOfDay = now.atZone(ZoneId.systemDefault()).let { time ->
            time.hour * 60 + time.minute
        }
        val modules = settingsService.snapshot().refresh.effectiveModuleSettings()
            .filter { (module, config) ->
                module in MoFishRefreshModule.visibleModules &&
                    config.enabled &&
                    isWithinAutoRefreshWindow(
                        currentMinuteOfDay = currentMinuteOfDay,
                        startMinuteOfDay = config.startMinuteOfDay,
                        endMinuteOfDay = config.endMinuteOfDay,
                    ) &&
                    (force || shouldRefreshModule(module, config.intervalSeconds, now))
            }
            .keys
        if (modules.isEmpty()) {
            return
        }
        lastAutoRefreshAtByModule = lastAutoRefreshAtByModule + modules.associateWith { now }
        projectService.refreshModules(project.name, modules, force = force)
    }

    private fun shouldRefreshModule(
        module: MoFishRefreshModule,
        intervalSeconds: Int,
        now: Instant,
    ): Boolean {
        val lastRefreshAt = lastAutoRefreshAtByModule[module] ?: return true
        return java.time.Duration.between(lastRefreshAt, now).seconds >= intervalSeconds.coerceAtLeast(1)
    }

    /**
     * 切换工具窗口当前选中的模块视图。
     * @param viewId 视图Id。
     */
    fun selectView(viewId: String) {
        projectService.selectView(viewId)
    }

    /**
     * 切换当前选中的资产代码，并发布选择变更事件。
     * @param assetCode 资产代码。
     */
    fun selectAsset(assetCode: String?) {
        projectService.selectAsset(assetCode)
    }

    /**
     * 添加基金代码。
     * @param code 资产代码或业务标识。
     */
    fun addFundCode(code: String) {
        val normalizedCode = normalizeFundCode(code)
        if (normalizedCode.isEmpty()) {
            return
        }
        updateWatchlist { watchlist ->
            watchlist.copy(
                fundCodes = upsertWatchlistCode(watchlist.fundCodes, normalizedCode),
            )
        }
    }

    /**
     * 删除基金代码。
     * @param code 资产代码或业务标识。
     */
    fun removeFundCode(code: String) {
        val normalizedCode = normalizeFundCode(code)
        if (normalizedCode.isEmpty()) {
            return
        }
        updateWatchlist { watchlist ->
            watchlist.copy(
                fundCodes = removeWatchlistCode(watchlist.fundCodes, normalizedCode),
            )
        }
    }

    /**
     * 添加股票代码。
     * @param code 资产代码或业务标识。
     */
    fun addStockCode(code: String) {
        val normalizedCode = normalizeStockCode(code)
        if (normalizedCode.isEmpty()) {
            return
        }
        updateWatchlist { watchlist ->
            watchlist.copy(
                stockCodes = upsertWatchlistCode(watchlist.stockCodes, normalizedCode),
            )
        }
    }

    /**
     * 添加市场指数代码。
     * @param code 资产代码或业务标识。
     */
    fun addIndexCode(code: String) {
        val normalizedCode = normalizeIndexCode(code)
        if (normalizedCode.isEmpty()) {
            return
        }
        updateWatchlist { watchlist ->
            watchlist.copy(
                indexCodes = upsertWatchlistCode(watchlist.indexCodes, normalizedCode),
            )
        }
    }

    /**
     * 添加外汇币种代码。
     * @param code 资产代码或业务标识。
     */
    fun addForexCode(code: String) {
        val normalizedCode = normalizeForexCode(code)
        if (normalizedCode.isEmpty()) {
            return
        }
        updateWatchlist { watchlist ->
            watchlist.copy(
                forexCurrencyCodes = upsertWatchlistCode(watchlist.forexCurrencyCodes, normalizedCode),
            )
        }
    }

    /**
     * 添加股票Group。
     * @param groupName group名称。
     */
    fun addStockGroup(groupName: String) {
        val normalizedGroup = normalizeStockGroupName(groupName)
        if (normalizedGroup.isEmpty()) {
            return
        }
        updateWatchlist { watchlist ->
            watchlist.copy(
                stockGroups = upsertStockGroup(watchlist.normalizedStockGroups(), normalizedGroup),
            )
        }
    }

    /**
     * 处理 assignStockToGroup 相关逻辑，并返回调用方需要的结果。
     * @param code 资产代码或业务标识。
     * @param groupName group名称。
     */
    fun assignStockToGroup(
        code: String,
        groupName: String,
    ) {
        val normalizedCode = normalizeStockCode(code)
        val normalizedGroup = normalizeStockGroupName(groupName)
        if (normalizedCode.isEmpty()) {
            return
        }
        updateWatchlist { watchlist ->
            val assignments = watchlist.stockGroupAssignments
                .filterKeys { !it.equals(normalizedCode, ignoreCase = true) }
            if (normalizedGroup.isEmpty()) {
                return@updateWatchlist watchlist.copy(stockGroupAssignments = assignments)
            }
            val groups = upsertStockGroup(watchlist.normalizedStockGroups(), normalizedGroup)
            watchlist.copy(
                stockGroups = groups,
                stockGroupAssignments = assignments.plus(normalizedCode to normalizedGroup),
            )
        }
    }

    /**
     * 删除股票Group。
     * @param groupName group名称。
     */
    fun removeStockGroup(groupName: String) {
        val normalizedGroup = normalizeStockGroupName(groupName)
        if (normalizedGroup.isEmpty()) {
            return
        }
        updateWatchlist { watchlist ->
            watchlist.copy(
                stockGroups = watchlist.normalizedStockGroups()
                    .filterNot { it.equals(normalizedGroup, ignoreCase = true) },
                stockGroupAssignments = watchlist.stockGroupAssignments
                    .filterValues { !it.equals(normalizedGroup, ignoreCase = true) },
            )
        }
    }

    /**
     * 处理 replaceStockGroups 相关逻辑，并返回调用方需要的结果。
     * @param groups groups。
     */
    fun replaceStockGroups(groups: List<String>) {
        val normalizedGroups = groups
            .map(::normalizeStockGroupName)
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
        updateWatchlist { watchlist ->
            val groupByLowercase = normalizedGroups.associateBy { it.lowercase() }
            watchlist.copy(
                stockGroups = normalizedGroups,
                stockGroupAssignments = watchlist.stockGroupAssignments
                    .mapNotNull { (code, group) ->
                        val normalizedGroup = groupByLowercase[group.lowercase()] ?: return@mapNotNull null
                        code to normalizedGroup
                    }
                    .toMap(),
            )
        }
    }

    /**
     * 处理 moveStockGroup 相关逻辑，并返回调用方需要的结果。
     * @param groupName group名称。
     * @param direction direction。
     */
    fun moveStockGroup(groupName: String, direction: Int) {
        val normalizedGroup = normalizeStockGroupName(groupName)
        if (normalizedGroup.isEmpty() || direction == 0) {
            return
        }
        updateWatchlist { watchlist ->
            val groups = watchlist.normalizedStockGroups().toMutableList()
            val index = groups.indexOfFirst { it.equals(normalizedGroup, ignoreCase = true) }
            if (index < 0) {
                return@updateWatchlist watchlist
            }
            val targetIndex = (index + direction).coerceIn(0, groups.lastIndex)
            if (index == targetIndex) {
                return@updateWatchlist watchlist
            }
            val movedGroup = groups.removeAt(index)
            groups.add(targetIndex, movedGroup)
            watchlist.copy(stockGroups = groups)
        }
    }

    /**
     * 添加虚拟币代码。
     * @param code 资产代码或业务标识。
     */
    fun addCryptoCode(code: String) {
        val normalizedCode = normalizeCryptoCode(code)
        if (normalizedCode.isEmpty()) {
            return
        }
        updateWatchlist { watchlist ->
            watchlist.copy(
                cryptoIds = upsertWatchlistCode(watchlist.cryptoIds, normalizedCode),
            )
        }
    }

    /**
     * 删除股票代码。
     * @param code 资产代码或业务标识。
     */
    fun removeStockCode(code: String) {
        val normalizedCode = normalizeStockCode(code)
        if (normalizedCode.isEmpty()) {
            return
        }
        updateWatchlist { watchlist ->
            watchlist.copy(
                stockCodes = removeWatchlistCode(watchlist.stockCodes, normalizedCode),
                stockGroupAssignments = watchlist.stockGroupAssignments
                    .filterKeys { !it.equals(normalizedCode, ignoreCase = true) },
            )
        }
    }

    /**
     * 删除市场指数代码。
     * @param code 资产代码或业务标识。
     */
    fun removeIndexCode(code: String) {
        val normalizedCode = normalizeIndexCode(code)
        if (normalizedCode.isEmpty()) {
            return
        }
        updateWatchlist { watchlist ->
            watchlist.copy(
                indexCodes = removeWatchlistCode(watchlist.indexCodes, normalizedCode),
            )
        }
    }

    /**
     * 删除外汇币种代码。
     * @param code 资产代码或业务标识。
     */
    fun removeForexCode(code: String) {
        val normalizedCode = normalizeForexCode(code)
        if (normalizedCode.isEmpty()) {
            return
        }
        updateWatchlist { watchlist ->
            watchlist.copy(
                forexCurrencyCodes = removeWatchlistCode(watchlist.forexCurrencyCodes, normalizedCode),
            )
        }
    }

    /**
     * 删除虚拟币代码。
     * @param code 资产代码或业务标识。
     */
    fun removeCryptoCode(code: String) {
        val normalizedCode = normalizeCryptoCode(code)
        if (normalizedCode.isEmpty()) {
            return
        }
        updateWatchlist { watchlist ->
            watchlist.copy(
                cryptoIds = removeWatchlistCode(watchlist.cryptoIds, normalizedCode),
            )
        }
    }

    /**
     * 处理 searchStockSuggestions 相关逻辑，并返回调用方需要的结果。
     * @param keyword 用户输入的搜索关键字。
     * @return 处理后的结果或当前状态。
     */
    fun searchStockSuggestions(keyword: String): List<StockSearchSuggestion> {
        val normalizedKeyword = keyword.trim()
        if (normalizedKeyword.isEmpty()) {
            return emptyList()
        }
        return stockQuoteClient.searchSuggestions(normalizedKeyword)
    }

    /**
     * 处理 searchCryptoSuggestions 相关逻辑，并返回调用方需要的结果。
     * @param keyword 用户输入的搜索关键字。
     * @return 处理后的结果或当前状态。
     */
    fun searchCryptoSuggestions(keyword: String): List<CryptoSearchSuggestion> {
        val normalizedKeyword = keyword.trim()
        if (normalizedKeyword.isEmpty()) {
            return emptyList()
        }
        return cryptoQuoteClient.searchSuggestions(normalizedKeyword)
    }

    /**
     * 处理 searchFundSuggestions 相关逻辑，并返回调用方需要的结果。
     * @param keyword 用户输入的搜索关键字。
     * @return 处理后的结果或当前状态。
     */
    fun searchFundSuggestions(keyword: String): List<FundSearchSuggestion> {
        val normalizedKeyword = keyword.trim()
        if (normalizedKeyword.isEmpty()) {
            return emptyList()
        }
        return fundQuoteClient.searchSuggestions(normalizedKeyword)
    }

    /**
     * 处理 searchForexSuggestions 相关逻辑，并返回调用方需要的结果。
     * @param keyword 用户输入的搜索关键字。
     * @return 处理后的结果或当前状态。
     */
    fun searchForexSuggestions(keyword: String): List<ForexRate> {
        val normalizedKeyword = keyword.trim()
        if (normalizedKeyword.isEmpty()) {
            return emptyList()
        }
        val cachedRates = snapshot()?.projectState?.workspace?.forexRates.orEmpty()
        val liveRates = runCatching { bocForexClient.fetchRates() }.getOrElse { cachedRates }
        val searchToken = normalizedKeyword.uppercase()
        return liveRates
            .filter { rate ->
                rate.currencyCode.uppercase().contains(searchToken) ||
                    rate.currencyName.uppercase().contains(searchToken)
            }
            .distinctBy { it.currencyCode.uppercase() }
    }

    /**
     * 转换为ggle行情SortDirection表示。
     */
    fun toggleQuoteSortDirection() {
        updateSettings { state ->
            val nextDirection = when (state.sortSettings.quoteDirection) {
                MoFishSortDirection.ASC -> MoFishSortDirection.DESC
                MoFishSortDirection.DESC -> MoFishSortDirection.ASC
            }
            state.copy(
                sortSettings = state.sortSettings.copy(quoteDirection = nextDirection),
            )
        }
    }

    /**
     * 返回当前服务或调度器的状态快照。
     * @return 处理后的结果或当前状态。
     */
    fun snapshot(): MoFishWatchlistState? = stateFlow.value

    /**
     * 处理 replaceStockHoldings 相关逻辑，并返回调用方需要的结果。
     * @param code 资产代码或业务标识。
     * @param holdings 一组资产持仓配置。
     */
    fun replaceStockHoldings(
        code: String,
        holdings: List<HoldingConfig>,
    ) {
        val normalizedCode = normalizeStockCode(code)
        if (normalizedCode.isEmpty()) {
            return
        }
        updateSettings { state ->
            state.copy(
                holdings = state.holdings
                    .filterNot { it.code.equals(normalizedCode, ignoreCase = true) && it.assetType == AssetType.STOCK } +
                        holdings,
            )
        }
    }

    /**
     * 添加持仓。
     * @param holdings 一组资产持仓配置。
     */
    fun addHoldings(holdings: List<HoldingConfig>) {
        val normalizedHoldings = holdings.filter { it.code.isNotBlank() }
        if (normalizedHoldings.isEmpty()) {
            return
        }
        updateSettings { state ->
            state.copy(
                holdings = mergeHoldings(state.holdings, normalizedHoldings),
            )
        }
    }

    /**
     * 处理 replaceStockReminders 相关逻辑，并返回调用方需要的结果。
     * @param code 资产代码或业务标识。
     * @param reminders 提醒。
     */
    fun replaceStockReminders(
        code: String,
        reminders: List<ReminderRule>,
    ) {
        val normalizedCode = normalizeStockCode(code)
        if (normalizedCode.isEmpty()) {
            return
        }
        updateSettings { state ->
            state.copy(
                reminders = state.reminders
                    .filterNot { it.code.equals(normalizedCode, ignoreCase = true) && it.assetType == AssetType.STOCK } +
                        reminders,
            )
        }
    }

    /**
     * 添加提醒。
     * @param reminders 提醒。
     */
    fun addReminders(reminders: List<ReminderRule>) {
        val normalizedReminders = reminders.filter { it.code.isNotBlank() }
        if (normalizedReminders.isEmpty()) {
            return
        }
        updateSettings { state ->
            state.copy(
                reminders = state.reminders + normalizedReminders,
            )
        }
    }

    /**
     * 更新Watchlist。
     * @param transform transform。
     */
    private fun updateWatchlist(transform: (MoFishWatchlistSettings) -> MoFishWatchlistSettings) {
        updateSettings { currentSettings ->
            currentSettings.copy(
                watchlist = transform(currentSettings.watchlist),
            )
        }
    }

    /**
     * 更新设置。
     * @param transform transform。
     */
    private fun updateSettings(transform: (MoFishSettingsState) -> MoFishSettingsState) {
        val currentSettings = settingsService.snapshot()
        val nextSettings = transform(currentSettings)
        if (nextSettings == currentSettings) {
            return
        }
        settingsService.replaceState(nextSettings)
        refresh(force = true)
    }
}

/**
 * 处理 mergeHoldings 相关逻辑，并返回调用方需要的结果。
 * @param currentHoldings 当前持仓。
 * @param addedHoldings added持仓。
 * @return 处理后的结果或当前状态。
 */
private fun mergeHoldings(
    currentHoldings: List<HoldingConfig>,
    addedHoldings: List<HoldingConfig>,
): List<HoldingConfig> {
    return addedHoldings.fold(currentHoldings) { current, added ->
        val existingIndex = current.indexOfFirst {
            it.assetType == added.assetType &&
                it.code.equals(added.code, ignoreCase = true) &&
                it.currency.equals(added.currency, ignoreCase = true) &&
                !it.isSellOut
        }
        if (existingIndex < 0) {
            current + added
        } else {
            current.toMutableList().apply {
                this[existingIndex] = mergeHolding(this[existingIndex], added)
            }
        }
    }
}

/**
 * 处理 mergeHolding 相关逻辑，并返回调用方需要的结果。
 * @param current 当前。
 * @param added added。
 * @return 处理后的结果或当前状态。
 */
private fun mergeHolding(
    current: HoldingConfig,
    added: HoldingConfig,
): HoldingConfig {
    val currentQuantity = current.quantity
    val addedQuantity = added.quantity
    val nextQuantity = when {
        currentQuantity != null && addedQuantity != null -> currentQuantity + addedQuantity
        addedQuantity != null -> addedQuantity
        else -> currentQuantity
    }
    val nextInvestedAmount = when {
        current.investedAmount != null && added.investedAmount != null -> current.investedAmount + added.investedAmount
        added.investedAmount != null -> added.investedAmount
        else -> current.investedAmount
    }
    val nextCostPrice = if (currentQuantity != null && addedQuantity != null && nextQuantity != null && nextQuantity > java.math.BigDecimal.ZERO) {
        ((current.costPrice * currentQuantity) + (added.costPrice * addedQuantity)).divide(
            nextQuantity,
            8,
            java.math.RoundingMode.HALF_UP,
        ).stripTrailingZeros()
    } else {
        added.costPrice
    }
    return current.copy(
        displayName = added.displayName.ifBlank { current.displayName },
        investedAmount = nextInvestedAmount,
        quantity = nextQuantity,
        costPrice = nextCostPrice,
        todayCostPrice = added.todayCostPrice ?: current.todayCostPrice,
        isSellOut = false,
    )
}

/**
 * 规范化基金代码，统一后续处理使用的表示形式。
 * @param rawCode 用户输入或接口返回的原始资产代码。
 * @return 处理后的结果或当前状态。
 */
internal fun normalizeFundCode(rawCode: String): String = rawCode.trim()

/**
 * 规范化股票代码，统一后续处理使用的表示形式。
 * @param rawCode 用户输入或接口返回的原始资产代码。
 * @return 处理后的结果或当前状态。
 */
internal fun normalizeStockCode(rawCode: String): String = rawCode.trim().lowercase()

/**
 * 规范化指数代码，统一后续处理使用的表示形式。
 * @param rawCode 用户输入或接口返回的原始资产代码。
 * @return 处理后的结果或当前状态。
 */
internal fun normalizeIndexCode(rawCode: String): String = rawCode.trim().lowercase()

/**
 * 规范化虚拟币代码，统一后续处理使用的表示形式。
 * @param rawCode 用户输入或接口返回的原始资产代码。
 * @return 处理后的结果或当前状态。
 */
internal fun normalizeCryptoCode(rawCode: String): String = rawCode.trim().lowercase()

/**
 * 规范化外汇币种代码，统一后续处理使用的表示形式。
 * @param rawCode 用户输入或接口返回的原始资产代码。
 * @return 处理后的结果或当前状态。
 */
internal fun normalizeForexCode(rawCode: String): String {
    val normalized = rawCode.trim()
    if (normalized.isEmpty()) {
        return ""
    }
    return buildForexCurrencyPairCode(normalized)
}

/**
 * 规范化股票Group名称，统一后续处理使用的表示形式。
 * @param rawGroupName rawGroup名称。
 * @return 处理后的结果或当前状态。
 */
internal fun normalizeStockGroupName(rawGroupName: String): String = normalizeStockGroupValue(rawGroupName)

/**
 * 处理 upsertStockGroup 相关逻辑，并返回调用方需要的结果。
 * @param currentGroups 当前Groups。
 * @param newGroup newGroup。
 * @return 处理后的结果或当前状态。
 */
internal fun upsertStockGroup(
    currentGroups: List<String>,
    newGroup: String,
): List<String> {
    val normalizedGroup = normalizeStockGroupName(newGroup)
    if (normalizedGroup.isEmpty()) {
        return currentGroups
    }
    return (currentGroups + normalizedGroup)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }
}

/**
 * 处理 upsertWatchlistCode 相关逻辑，并返回调用方需要的结果。
 * @param currentCodes 当前Codes。
 * @param newCode new代码。
 * @return 处理后的结果或当前状态。
 */
internal fun upsertWatchlistCode(
    currentCodes: List<String>,
    newCode: String,
): List<String> {
    val normalizedCode = newCode.trim()
    if (normalizedCode.isEmpty()) {
        return currentCodes
    }
    return (currentCodes + normalizedCode).distinctBy { it.lowercase() }
}

/**
 * 删除Watchlist代码。
 * @param currentCodes 当前Codes。
 * @param targetCode target代码。
 * @return 处理后的结果或当前状态。
 */
internal fun removeWatchlistCode(
    currentCodes: List<String>,
    targetCode: String,
): List<String> {
    val normalizedCode = targetCode.trim()
    if (normalizedCode.isEmpty()) {
        return currentCodes
    }
    return currentCodes.filterNot { it.equals(normalizedCode, ignoreCase = true) }
}
