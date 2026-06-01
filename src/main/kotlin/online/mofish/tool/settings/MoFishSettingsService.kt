package online.mofish.tool.settings

import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@State(
    name = "MoFishSettings",
    storages = [Storage("mofish.xml")],
)
@Service(Service.Level.APP)
class MoFishSettingsService : SerializablePersistentStateComponent<MoFishSettingsState>(MoFishSettingsState()) {
    private val stateFlow = MutableStateFlow(state)

    val states: StateFlow<MoFishSettingsState> = stateFlow.asStateFlow()

    /**
     * 加载状态数据。
     * @param state 状态。
     */
    override fun loadState(state: MoFishSettingsState) {
        super.loadState(state)
        stateFlow.value = this.state
    }

    /**
     * 返回当前服务或调度器的状态快照。
     * @return 处理后的结果或当前状态。
     */
    fun snapshot(): MoFishSettingsState = state

    /**
     * 处理 replaceState 相关逻辑，并返回调用方需要的结果。
     * @param newState new状态。
     * @return 处理后的结果或当前状态。
     */
    fun replaceState(newState: MoFishSettingsState): MoFishSettingsState {
        return publishState { newState }
    }

    /**
     * 根据当前选择和上下文更新动作可用状态。
     * @param transform transform。
     * @return 处理后的结果或当前状态。
     */
    fun update(transform: (MoFishSettingsState) -> MoFishSettingsState): MoFishSettingsState {
        return publishState(transform)
    }

    /**
     * 处理 publishState 相关逻辑，并返回调用方需要的结果。
     * @param transform transform。
     * @return 处理后的结果或当前状态。
     */
    private fun publishState(transform: (MoFishSettingsState) -> MoFishSettingsState): MoFishSettingsState {
        val next = updateState(transform)
        stateFlow.value = next
        return next
    }
}
