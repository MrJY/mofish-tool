package online.mofish.tool.data

import online.mofish.tool.domain.MoFishWorkspace
import online.mofish.tool.domain.MoFishRefreshModule
import online.mofish.tool.settings.MoFishSettingsState

interface MoFishDataSource {
    /**
     * 创建一个轻量级工作区骨架，用于界面首次打开时先展示占位状态。
     * @param projectName 当前 IntelliJ 项目的名称，用于区分不同项目的缓存、状态和刷新任务。
     * @param settings 当前摸鱼工具设置快照，提供关注列表、持仓、提醒和刷新配置。
     * @return 处理后的结果或当前状态。
     */
    fun createSkeletonWorkspace(
        projectName: String,
        settings: MoFishSettingsState,
    ): MoFishWorkspace

    /**
     * 根据项目名称和当前设置加载完整工作区数据。
     * @param projectName 当前 IntelliJ 项目的名称，用于区分不同项目的缓存、状态和刷新任务。
     * @param settings 当前摸鱼工具设置快照，提供关注列表、持仓、提醒和刷新配置。
     * @return 处理后的结果或当前状态。
     */
    fun loadWorkspace(
        projectName: String,
        settings: MoFishSettingsState,
    ): MoFishWorkspace

    /**
     * 只刷新指定模块的数据，并把结果合并回当前工作区。
     * @param projectName 当前 IntelliJ 项目的名称，用于区分不同项目的缓存、状态和刷新任务。
     * @param settings 当前摸鱼工具设置快照，提供关注列表、持仓、提醒和刷新配置。
     * @param currentWorkspace 刷新前已有的工作区数据，用于保留未刷新模块的内容。
     * @param modules 需要刷新或处理的业务模块集合。
     * @return 处理后的结果或当前状态。
     */
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
