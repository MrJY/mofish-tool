package online.mofish.tool.state

import online.mofish.tool.domain.WorkspaceProfitSnapshot
import online.mofish.tool.settings.MoFishSettingsState

data class MoFishWatchlistState(
    val projectState: MoFishProjectState,
    val settingsState: MoFishSettingsState,
    val appState: MoFishAppState,
    val cacheState: MoFishMemoryCacheState,
    val schedulerState: MoFishRefreshSchedulerState,
    val profitSnapshot: WorkspaceProfitSnapshot,
) {
    val projectName: String
        get() = projectState.projectName

    val autoRefreshRegistered: Boolean
        get() = schedulerState.registeredProjects.contains(projectName)
}
