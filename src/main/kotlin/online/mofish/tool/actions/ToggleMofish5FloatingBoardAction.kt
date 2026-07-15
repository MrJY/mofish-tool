package online.mofish.tool.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import online.mofish.tool.services.Mofish5FloatingBoardController

class ToggleMofish5FloatingBoardAction : AnAction(), DumbAware {
    override fun update(event: AnActionEvent) {
        val controller = event.project?.service<Mofish5FloatingBoardController>()
        event.presentation.isEnabled = controller?.isAvailable == true
    }

    override fun actionPerformed(event: AnActionEvent) {
        event.project?.service<Mofish5FloatingBoardController>()?.toggle()
    }
}
