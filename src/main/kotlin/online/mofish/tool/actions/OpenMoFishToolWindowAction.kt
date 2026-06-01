package online.mofish.tool.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.ToolWindowManager
import online.mofish.tool.services.MoFishProjectService
import online.mofish.tool.ui.toolwindow.MoFishToolWindowFactory

class OpenMoFishToolWindowAction : AnAction(), DumbAware {
    /**
     * 处理用户触发的 IDE 动作。
     * @param event IntelliJ 平台传入的动作事件上下文。
     */
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        project.service<MoFishProjectService>().markToolWindowOpened(project.name)
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow(MoFishToolWindowFactory.TOOL_WINDOW_ID)
            ?: return

        toolWindow.show()
    }
}
