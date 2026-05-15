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

    override fun loadState(state: MoFishSettingsState) {
        super.loadState(state)
        stateFlow.value = this.state
    }

    fun snapshot(): MoFishSettingsState = state

    fun replaceState(newState: MoFishSettingsState): MoFishSettingsState {
        return publishState { newState }
    }

    fun update(transform: (MoFishSettingsState) -> MoFishSettingsState): MoFishSettingsState {
        return publishState(transform)
    }

    private fun publishState(transform: (MoFishSettingsState) -> MoFishSettingsState): MoFishSettingsState {
        val next = updateState(transform)
        stateFlow.value = next
        return next
    }
}
