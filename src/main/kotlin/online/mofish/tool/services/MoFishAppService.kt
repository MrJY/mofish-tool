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

    @Synchronized
    fun markProjectActivated(projectName: String) {
        updateState { state ->
            state.copy(
                activeProjectNames = state.activeProjectNames + projectName,
                lastActivatedProject = projectName,
            )
        }
    }

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

    @Synchronized
    fun snapshot(): MoFishAppState = stateFlow.value

    private fun updateState(transform: (MoFishAppState) -> MoFishAppState): MoFishAppState {
        val next = transform(stateFlow.value)
        stateFlow.value = next
        return next
    }
}
