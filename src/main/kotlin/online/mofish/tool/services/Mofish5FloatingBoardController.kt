package online.mofish.tool.services

import com.intellij.openapi.components.Service

@Service(Service.Level.PROJECT)
class Mofish5FloatingBoardController {
    private var toggleHandler: (() -> Unit)? = null

    val isAvailable: Boolean
        get() = toggleHandler != null

    fun register(handler: () -> Unit) {
        toggleHandler = handler
    }

    fun unregister(handler: () -> Unit) {
        if (toggleHandler === handler) {
            toggleHandler = null
        }
    }

    fun toggle() {
        toggleHandler?.invoke()
    }
}
