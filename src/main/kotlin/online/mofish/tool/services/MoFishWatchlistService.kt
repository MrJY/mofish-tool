package online.mofish.tool.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import online.mofish.tool.data.crypto.CryptoQuoteClient
import online.mofish.tool.data.fund.FundQuoteClient
import online.mofish.tool.data.stock.StockQuoteClient
import online.mofish.tool.domain.CryptoSearchSuggestion
import online.mofish.tool.domain.FundSearchSuggestion
import online.mofish.tool.domain.MoFishRefreshModule
import online.mofish.tool.domain.StockSearchSuggestion
import online.mofish.tool.settings.MoFishQuoteSortField
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
    private val cryptoQuoteClient = CryptoQuoteClient()
    private val fundQuoteClient = FundQuoteClient()
    private val stockQuoteClient = StockQuoteClient()
    private val profitCalculator = MoFishProfitCalculator()
    private val stateFlow = MutableStateFlow<MoFishWatchlistState?>(null)

    @Volatile
    private var activated = false

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

    fun activate() {
        if (activated) {
            return
        }
        activated = true
        projectService.ensureState(project.name)
        refreshSchedulerService.registerProject(project.name) {
            refreshConfiguredModules(force = true)
        }
    }

    fun deactivate() {
        if (!activated) {
            return
        }
        activated = false
        refreshSchedulerService.unregisterProject(project.name)
    }

    fun dispose() {
        deactivate()
        scope.cancel()
    }

    fun refresh(force: Boolean = false) {
        scope.launch(Dispatchers.IO) {
            projectService.refreshState(project.name, force = force)
        }
    }

    fun refreshModule(module: MoFishRefreshModule, force: Boolean = true) {
        refreshModules(setOf(module), force)
    }

    fun refreshModules(modules: Set<MoFishRefreshModule>, force: Boolean = true) {
        scope.launch(Dispatchers.IO) {
            projectService.refreshModules(project.name, modules, force = force)
        }
    }

    private fun refreshConfiguredModules(force: Boolean = true) {
        val modules = settingsService.snapshot().refresh.autoRefreshModules
        if (modules.isEmpty()) {
            return
        }
        projectService.refreshModules(project.name, modules, force = force)
    }

    fun selectView(viewId: String) {
        projectService.selectView(viewId)
    }

    fun selectAsset(assetCode: String?) {
        projectService.selectAsset(assetCode)
    }

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

    fun searchStockSuggestions(keyword: String): List<StockSearchSuggestion> {
        val normalizedKeyword = keyword.trim()
        if (normalizedKeyword.isEmpty()) {
            return emptyList()
        }
        return stockQuoteClient.searchSuggestions(normalizedKeyword)
    }

    fun searchCryptoSuggestions(keyword: String): List<CryptoSearchSuggestion> {
        val normalizedKeyword = keyword.trim()
        if (normalizedKeyword.isEmpty()) {
            return emptyList()
        }
        return cryptoQuoteClient.searchSuggestions(normalizedKeyword)
    }

    fun searchFundSuggestions(keyword: String): List<FundSearchSuggestion> {
        val normalizedKeyword = keyword.trim()
        if (normalizedKeyword.isEmpty()) {
            return emptyList()
        }
        return fundQuoteClient.searchSuggestions(normalizedKeyword)
    }

    fun cycleQuoteSortField() {
        val currentField = settingsService.snapshot().sortSettings.quoteField
        val fields = MoFishQuoteSortField.entries
        val nextField = fields[(fields.indexOf(currentField) + 1) % fields.size]
        updateSettings { state ->
            state.copy(
                sortSettings = state.sortSettings.copy(quoteField = nextField),
            )
        }
    }

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

    fun snapshot(): MoFishWatchlistState? = stateFlow.value

    private fun updateWatchlist(transform: (MoFishWatchlistSettings) -> MoFishWatchlistSettings) {
        updateSettings { currentSettings ->
            currentSettings.copy(
                watchlist = transform(currentSettings.watchlist),
            )
        }
    }

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

internal fun normalizeFundCode(rawCode: String): String = rawCode.trim()

internal fun normalizeStockCode(rawCode: String): String = rawCode.trim().lowercase()

internal fun normalizeCryptoCode(rawCode: String): String = rawCode.trim().lowercase()

internal fun normalizeStockGroupName(rawGroupName: String): String = normalizeStockGroupValue(rawGroupName)

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
