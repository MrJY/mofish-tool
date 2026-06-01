package online.mofish.tool.services

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.components.Service
import online.mofish.tool.state.MoFishAppState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Service(Service.Level.APP)
class MoFishAppService {
    private val applicationInfo = ApplicationInfo.getInstance()

    private val stateFlow = MutableStateFlow(
        MoFishAppState(
            ideProductName = applicationInfo.fullApplicationName,
            ideFullVersion = applicationInfo.fullVersion,
            ideBuild = applicationInfo.build.asString(),
        )
    )

    val states: StateFlow<MoFishAppState> = stateFlow.asStateFlow()

    /**
     * 记录项目被激活，并更新应用级状态。
     * @param projectName 当前 IntelliJ 项目的名称，用于区分不同项目的缓存、状态和刷新任务。
     */
    @Synchronized
    fun markProjectActivated(projectName: String) {
        updateState { state ->
            state.copy(
                activeProjectNames = state.activeProjectNames + projectName,
                lastActivatedProject = projectName,
            )
        }
    }

    /**
     * 记录工具窗口已经被打开，用于维护应用级使用状态。
     * @param projectName 当前 IntelliJ 项目的名称，用于区分不同项目的缓存、状态和刷新任务。
     */
    @Synchronized
    fun markToolWindowOpened(projectName: String) {
        markProjectActivated(projectName)
        updateState { state ->
            state.copy(
                toolWindowOpenCount = state.toolWindowOpenCount + 1,
                lastToolWindowProject = projectName,
            )
        }
    }

    /**
     * 返回当前服务或调度器的状态快照。
     * @return 处理后的结果或当前状态。
     */
    @Synchronized
    fun snapshot(): MoFishAppState = stateFlow.value

    /**
     * 更新状态。
     * @param transform transform。
     * @return 处理后的结果或当前状态。
     */
    private fun updateState(transform: (MoFishAppState) -> MoFishAppState): MoFishAppState {
        val next = transform(stateFlow.value)
        stateFlow.value = next
        return next
    }
}
