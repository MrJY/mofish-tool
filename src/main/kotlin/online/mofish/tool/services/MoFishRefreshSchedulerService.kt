package online.mofish.tool.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import online.mofish.tool.settings.MoFishSettingsService
import online.mofish.tool.settings.MoFishSettingsState
import online.mofish.tool.settings.normalizeMinuteOfDay
import online.mofish.tool.state.MoFishRefreshSchedulerConfig
import online.mofish.tool.state.MoFishRefreshSchedulerState

@Service(Service.Level.APP)
class MoFishRefreshSchedulerService : Disposable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val scheduler = MoFishRefreshScheduler(scope = scope)
    private val settingsService: MoFishSettingsService by lazy { service() }

    @Volatile
    private var settingsObserverStarted = false

    val states: StateFlow<MoFishRefreshSchedulerState> = scheduler.states

    /**
     * Synchronizes scheduler settings lazily. Application services can be constructed while the
     * platform is still loading persisted configuration, so avoid touching other services in the
     * constructor path.
     */
    private fun ensureSettingsObserverStarted() {
        if (settingsObserverStarted) {
            return
        }
        synchronized(this) {
            if (settingsObserverStarted) {
                return
            }
            settingsObserverStarted = true
        }
        scheduler.updateConfig(settingsService.snapshot().toRefreshSchedulerConfig())
        scope.launch {
            settingsService.states.collectLatest { state ->
                scheduler.updateConfig(state.toRefreshSchedulerConfig())
            }
        }
    }

    /**
     * 注册项目刷新回调，使调度器可以按配置触发项目刷新。
     * @param projectName 当前 IntelliJ 项目的名称，用于区分不同项目的缓存、状态和刷新任务。
     * @param refreshAction 刷新动作。
     */
    fun registerProject(
        projectName: String,
        refreshAction: suspend (Boolean) -> Unit,
    ) {
        ensureSettingsObserverStarted()
        scheduler.registerProject(projectName, refreshAction)
    }

    /**
     * 注销项目刷新回调，并停止该项目的自动刷新任务。
     * @param projectName 当前 IntelliJ 项目的名称，用于区分不同项目的缓存、状态和刷新任务。
     */
    fun unregisterProject(projectName: String) {
        scheduler.unregisterProject(projectName)
    }

    /**
     * 立即触发指定项目的刷新流程。
     * @param projectName 当前 IntelliJ 项目的名称，用于区分不同项目的缓存、状态和刷新任务。
     * @param force 是否跳过缓存并强制重新读取数据。
     * @return 处理后的结果或当前状态。
     */
    fun refreshNow(
        projectName: String,
        force: Boolean = false,
    ) {
        ensureSettingsObserverStarted()
        scheduler.triggerNow(projectName, force)
    }

    /**
     * 返回当前服务或调度器的状态快照。
     * @return 处理后的结果或当前状态。
     */
    fun snapshot(): MoFishRefreshSchedulerState {
        ensureSettingsObserverStarted()
        return scheduler.snapshot()
    }

    /**
     * 释放服务持有的后台任务和运行资源。
     */
    override fun dispose() {
        scheduler.close()
        scope.cancel()
    }
}

private fun MoFishSettingsState.toRefreshSchedulerConfig(): MoFishRefreshSchedulerConfig {
    return MoFishRefreshSchedulerConfig(
        autoRefreshEnabled = refresh.autoRefreshEnabled,
        intervalSeconds = refresh.intervalSeconds,
        autoRefreshStartMinuteOfDay = normalizeMinuteOfDay(refresh.autoRefreshStartMinuteOfDay),
        autoRefreshEndMinuteOfDay = normalizeMinuteOfDay(refresh.autoRefreshEndMinuteOfDay),
    )
}
