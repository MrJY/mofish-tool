package online.mofish.tool.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import kotlinx.coroutines.flow.*
import online.mofish.tool.domain.MoFishRefreshModule
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

    /**
     * 确保指定项目已经拥有可用状态，没有状态时创建占位状态。
     * @param projectName 当前 IntelliJ 项目的名称，用于区分不同项目的缓存、状态和刷新任务。
     * @return 处理后的结果或当前状态。
     */
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
            moduleRefreshAt = MoFishRefreshModule.entries.associateWith { now },
            loadOrigin = WorkspaceLoadOrigin.PLACEHOLDER,
            cacheHit = false,
        )
        stateFlow.value = placeholderState
        return placeholderState
    }

    /**
     * 获取指定项目的当前状态，必要时触发一次完整刷新。
     * @param projectName 当前 IntelliJ 项目的名称，用于区分不同项目的缓存、状态和刷新任务。
     * @return 处理后的结果或当前状态。
     */
    @Synchronized
    fun getState(projectName: String): MoFishProjectState {
        val current = stateFlow.value
        return if (current != null && current.projectName == projectName) {
            current
        } else {
            refreshState(projectName)
        }
    }

    /**
     * 刷新指定项目的完整工作区状态，并同步缓存与刷新事件。
     * @param projectName 当前 IntelliJ 项目的名称，用于区分不同项目的缓存、状态和刷新任务。
     * @param force 是否跳过缓存并强制重新读取数据。
     * @return 处理后的结果或当前状态。
     */
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
            moduleRefreshAt = MoFishRefreshModule.entries.associateWith { now },
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

    /**
     * 刷新指定的一组业务模块，并保留其他模块已有数据。
     * @param projectName 当前 IntelliJ 项目的名称，用于区分不同项目的缓存、状态和刷新任务。
     * @param modules 需要刷新或处理的业务模块集合。
     * @param force 是否跳过缓存并强制重新读取数据。
     * @return 处理后的结果或当前状态。
     */
    @Synchronized
    fun refreshModules(
        projectName: String,
        modules: Set<MoFishRefreshModule>,
        force: Boolean = false,
    ): MoFishProjectState {
        if (modules.isEmpty()) {
            return getState(projectName)
        }

        val now = Instant.now()
        val settingsSnapshot = settings
        val previous = stateFlow.value ?: ensureState(projectName)
        val workspace = dataSource.loadWorkspaceModules(
            projectName = projectName,
            settings = settingsSnapshot,
            currentWorkspace = previous.workspace,
            modules = modules,
        )
        service<MoFishMemoryCacheService>().putWorkspace(projectName, workspace, now)

        val newState = previous.copy(
            workspace = workspace,
            lastRefreshAt = now,
            moduleRefreshAt = previous.moduleRefreshAt + modules.associateWith { now },
            loadOrigin = WorkspaceLoadOrigin.DATA_SOURCE,
            cacheHit = false,
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

    /**
     * 记录工具窗口已经被打开，用于维护应用级使用状态。
     * @param projectName 当前 IntelliJ 项目的名称，用于区分不同项目的缓存、状态和刷新任务。
     */
    fun markToolWindowOpened(projectName: String) {
        service<MoFishAppService>().markToolWindowOpened(projectName)
    }

    /**
     * 切换工具窗口当前选中的模块视图。
     * @param viewId 视图Id。
     */
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

    /**
     * 切换当前选中的资产代码，并发布选择变更事件。
     * @param assetCode 资产代码。
     */
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

    /**
     * 获取App状态。
     * @return 处理后的结果或当前状态。
     */
    fun getAppState(): MoFishAppState = service<MoFishAppService>().snapshot()

    /**
     * 获取MemoryCache状态。
     * @return 处理后的结果或当前状态。
     */
    fun getMemoryCacheState(): MoFishMemoryCacheState = service<MoFishMemoryCacheService>().snapshot()

    /**
     * 根据项目名称和当前设置加载完整工作区数据。
     * @param projectName 当前 IntelliJ 项目的名称，用于区分不同项目的缓存、状态和刷新任务。
     * @return 处理后的结果或当前状态。
     */
    fun loadWorkspace(projectName: String) = getState(projectName).workspace

    private fun MoFishWorkspace.matches(settingsState: MoFishSettingsState): Boolean {
        return fundQuotes.map { it.code } == settingsState.watchlist.fundCodes &&
                stockQuotes.map { it.code } == settingsState.watchlist.stockCodes &&
                cryptoQuotes.map { it.code } == settingsState.watchlist.cryptoIds &&
                holdings == settingsState.holdings &&
                reminderRules == settingsState.reminders &&
                aiConfig == settingsState.aiConfig
    }

    /**
     * 处理 publishSelectionChanged 相关逻辑，并返回调用方需要的结果。
     * @param state 状态。
     */
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
