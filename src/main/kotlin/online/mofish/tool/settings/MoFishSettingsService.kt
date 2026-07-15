package online.mofish.tool.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@State(
    name = "MoFishSettings",
    storages = [Storage("mofish.xml")],
)
class MoFishSettingsService : PersistentStateComponent<MoFishSettingsPersistedState> {
    @Volatile
    private var currentState = MoFishSettingsState()
    private val stateFlow = MutableStateFlow(currentState)
    private val dirty = AtomicBoolean(false)

    val states: StateFlow<MoFishSettingsState> = stateFlow.asStateFlow()

    init {
        AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
            ::saveDirtySettings,
            SETTINGS_FALLBACK_SAVE_INTERVAL_SECONDS,
            SETTINGS_FALLBACK_SAVE_INTERVAL_SECONDS,
            TimeUnit.SECONDS,
        )
    }

    override fun getState(): MoFishSettingsPersistedState {
        return MoFishSettingsPersistedState().apply {
            payload = encodeSettingsState(currentState)
        }
    }

    /**
     * 加载状态数据。
     * @param state 状态。
     */
    override fun loadState(state: MoFishSettingsPersistedState) {
        val loadedState = decodeSettingsState(state.payload)
        currentState = loadedState
        stateFlow.value = loadedState
    }

    /**
     * 返回当前服务或调度器的状态快照。
     * @return 处理后的结果或当前状态。
     */
    fun snapshot(): MoFishSettingsState = currentState

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
        val next = transform(currentState)
        currentState = next
        stateFlow.value = next
        dirty.set(true)
        ApplicationManager.getApplication().saveSettings()
        return next
    }

    private fun saveDirtySettings() {
        if (!dirty.compareAndSet(true, false)) {
            return
        }
        runCatching {
            ApplicationManager.getApplication().saveSettings()
        }.onFailure {
            dirty.set(true)
        }
    }
}

private const val SETTINGS_FALLBACK_SAVE_INTERVAL_SECONDS = 30L

class MoFishSettingsPersistedState {
    var payload: String = ""
}

internal fun encodeSettingsState(state: MoFishSettingsState): String {
    val serializableState = state.copy(
        refresh = state.refresh.copy(autoRefreshModules = state.refresh.autoRefreshModules.toSet()),
        ui = state.ui.copy(
            stockTableColumns = state.ui.stockTableColumns.toSet(),
            enabledModules = state.ui.enabledModules.toSet(),
        ),
        statusBar = state.statusBar.copy(enabledModules = state.statusBar.enabledModules.toSet()),
    )
    val bytes = ByteArrayOutputStream().use { byteStream ->
        ObjectOutputStream(byteStream).use { objectStream ->
            objectStream.writeObject(serializableState)
        }
        byteStream.toByteArray()
    }
    return Base64.getEncoder().encodeToString(bytes)
}

internal fun decodeSettingsState(payload: String): MoFishSettingsState {
    if (payload.isBlank()) {
        return MoFishSettingsState()
    }
    return runCatching {
        val bytes = Base64.getDecoder().decode(payload)
        ByteArrayInputStream(bytes).use { byteStream ->
            ObjectInputStream(byteStream).use { objectStream ->
                objectStream.readObject() as MoFishSettingsState
            }
        }
    }.getOrDefault(MoFishSettingsState())
}
