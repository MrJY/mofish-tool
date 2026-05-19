package online.mofish.tool.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import kotlinx.coroutines.flow.*
import online.mofish.tool.data.MoFishDataSource
import online.mofish.tool.data.RemoteMoFishDataSource
import online.mofish.tool.domain.MoFishWorkspace
import online.mofish.tool.settings.MoFishSettingsService
import online.mofish.tool.settings.MoFishSettingsState
import online.mofish.tool.state.*
import java.time.Instant

@Service(Service.Level.PROJECT)
class MoFishProjectService(
    private val dataSource: MoFishDataSource = RemoteMoFishDataSource(),
) {
    private val stateFlow = MutableStateFlow<MoFishProjectState?>(null)
    private val eventFlow = MutableSharedFlow<MoFishProjectEvent>(replay = 1, extraBufferCapacity = 16)

    val states: StateFlow<MoFishProjectState?> = stateFlow.asStateFlow()
    val events: SharedFlow<MoFishProjectEvent> = eventFlow.asSharedFlow()
    val settings: MoFishSettingsState
        get() = service<MoFishSettingsService>().snapshot()

    val settingStates: StateFlow<MoFishSettingsState>
        get() = service<MoFishSettingsService>().states

    @Synchronized
    fun ensureState(projectName: String): MoFishProjectState {
        val current = stateFlow.value
        if (current != null && current.projectName == projectName) {
            return current
        }

        val now = Instant.now()
        val settingsSnapshot = settings
        val placeholderState = MoFishProjectState(
            projectName = projectName,
            workspace = dataSource.createSkeletonWorkspace(projectName, settingsSnapshot),
            selectedViewId = current?.selectedViewId ?: DEFAULT_VIEW_ID,
            selectedAssetCode = current?.selectedAssetCode,
            lastRefreshAt = now,
            loadOrigin = WorkspaceLoadOrigin.PLACEHOLDER,
            cacheHit = false,
        )
        stateFlow.value = placeholderState
        return placeholderState
    }

    @Synchronized
    fun getState(projectName: String): MoFishProjectState {
        val current = stateFlow.value
        return if (current != null && current.projectName == projectName) {
            current
        } else {
            refreshState(projectName)
        }
    }

    @Synchronized
    fun refreshState(projectName: String, force: Boolean = false): MoFishProjectState {
        val now = Instant.now()
        val appService = service<MoFishAppService>()
        val cacheService = service<MoFishMemoryCacheService>()
        val settingsSnapshot = settings

        appService.markProjectActivated(projectName)

        val previous = stateFlow.value
        // Cache entries are only reusable while they still mirror the user's settings. This keeps
        // edits to watchlists, holdings, reminders, or AI config from showing stale rows until the
        // next manual refresh.
        val cachedEntry = if (force) {
            cacheService.invalidateWorkspace(projectName)
            null
        } else {
            cacheService.getWorkspace(projectName, now)?.takeIf { entry ->
                entry.workspace.matches(settingsSnapshot)
            } ?: run {
                cacheService.invalidateWorkspace(projectName)
                null
            }
        }

        val workspace = cachedEntry?.workspace ?: dataSource.loadWorkspace(projectName, settingsSnapshot).also {
            cacheService.putWorkspace(projectName, it, now)
        }

        val newState = MoFishProjectState(
            projectName = projectName,
            workspace = workspace,
            selectedViewId = previous?.selectedViewId ?: DEFAULT_VIEW_ID,
            selectedAssetCode = previous?.selectedAssetCode,
            lastRefreshAt = now,
            loadOrigin = if (cachedEntry == null) WorkspaceLoadOrigin.DATA_SOURCE else WorkspaceLoadOrigin.MEMORY_CACHE,
            cacheHit = cachedEntry != null,
        )
        stateFlow.value = newState
        eventFlow.tryEmit(
            MoFishWorkspaceRefreshedEvent(
                projectName = projectName,
                forced = force,
                loadOrigin = newState.loadOrigin,
                cacheHit = newState.cacheHit,
                occurredAt = now,
            )
        )
        return newState
    }

    fun markToolWindowOpened(projectName: String) {
        service<MoFishAppService>().markToolWindowOpened(projectName)
    }

    @Synchronized
    fun selectView(viewId: String) {
        val current = stateFlow.value ?: return
        if (current.selectedViewId == viewId) {
            return
        }
        val next = current.copy(selectedViewId = viewId)
        stateFlow.value = next
        publishSelectionChanged(next)
    }

    @Synchronized
    fun selectAsset(assetCode: String?) {
        val current = stateFlow.value ?: return
        if (current.selectedAssetCode.equals(assetCode, ignoreCase = true)) {
            return
        }
        val next = current.copy(selectedAssetCode = assetCode)
        stateFlow.value = next
        publishSelectionChanged(next)
    }

    fun getAppState(): MoFishAppState = service<MoFishAppService>().snapshot()

    fun getMemoryCacheState(): MoFishMemoryCacheState = service<MoFishMemoryCacheService>().snapshot()

    fun loadWorkspace(projectName: String) = getState(projectName).workspace

    private fun MoFishWorkspace.matches(settingsState: MoFishSettingsState): Boolean {
        return fundQuotes.map { it.code } == settingsState.watchlist.fundCodes &&
                stockQuotes.map { it.code } == settingsState.watchlist.stockCodes &&
                cryptoQuotes.map { it.code } == settingsState.watchlist.cryptoIds &&
                holdings == settingsState.holdings &&
                reminderRules == settingsState.reminders &&
                aiConfig == settingsState.aiConfig
    }

    private fun publishSelectionChanged(state: MoFishProjectState) {
        eventFlow.tryEmit(
            MoFishSelectionChangedEvent(
                projectName = state.projectName,
                selectedViewId = state.selectedViewId,
                selectedAssetCode = state.selectedAssetCode,
            )
        )
    }

    companion object {
        private const val DEFAULT_VIEW_ID = "stocks"
    }
}
