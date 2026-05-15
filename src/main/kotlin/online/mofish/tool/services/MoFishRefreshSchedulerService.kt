package online.mofish.tool.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import online.mofish.tool.settings.MoFishSettingsService
import online.mofish.tool.settings.MoFishSettingsState
import online.mofish.tool.settings.normalizeMinuteOfDay
import online.mofish.tool.state.MoFishRefreshSchedulerConfig
import online.mofish.tool.state.MoFishRefreshSchedulerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Service(Service.Level.APP)
class MoFishRefreshSchedulerService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val settingsService = service<MoFishSettingsService>()
    private val scheduler = MoFishRefreshScheduler(scope = scope)

    val states: StateFlow<MoFishRefreshSchedulerState> = scheduler.states

    init {
        scheduler.updateConfig(settingsService.snapshot().toRefreshSchedulerConfig())
        scope.launch {
            settingsService.states.collectLatest { state ->
                scheduler.updateConfig(state.toRefreshSchedulerConfig())
            }
        }
    }

    fun registerProject(
        projectName: String,
        refreshAction: suspend (Boolean) -> Unit,
    ) {
        scheduler.registerProject(projectName, refreshAction)
    }

    fun unregisterProject(projectName: String) {
        scheduler.unregisterProject(projectName)
    }

    fun refreshNow(
        projectName: String,
        force: Boolean = false,
    ) {
        scheduler.triggerNow(projectName, force)
    }

    fun snapshot(): MoFishRefreshSchedulerState = scheduler.snapshot()

    fun dispose() {
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
