package online.mofish.tool.data

import online.mofish.tool.domain.MoFishWorkspace
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
}
