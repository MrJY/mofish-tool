package online.mofish.tool.data

import online.mofish.tool.domain.MoFishWorkspace
import online.mofish.tool.domain.MoFishRefreshModule
import online.mofish.tool.settings.MoFishSettingsState

interface MoFishDataSource {
    fun createSkeletonWorkspace(
        projectName: String,
        settings: MoFishSettingsState,
    ): MoFishWorkspace

    fun loadWorkspace(
        projectName: String,
        settings: MoFishSettingsState,
    ): MoFishWorkspace

    fun loadWorkspaceModules(
        projectName: String,
        settings: MoFishSettingsState,
        currentWorkspace: MoFishWorkspace,
        modules: Set<MoFishRefreshModule>,
    ): MoFishWorkspace {
        if (modules.isEmpty()) {
            return currentWorkspace
        }
        return loadWorkspace(projectName, settings)
    }
}
