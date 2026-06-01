package online.mofish.tool.ui.toolwindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class MoFishToolWindowFactory : ToolWindowFactory, DumbAware {
    /**
     * 创建并注册摸鱼工具窗口的内容组件。
     * @param project 当前 IntelliJ 项目实例。
     * @param toolWindow toolWindow。
     */
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = MoFishToolWindowPanel(project)
        val content = ContentFactory.getInstance().createContent(
            panel,
            "",
            false,
        )
        content.setDisposer(panel)

        toolWindow.contentManager.addContent(content)
    }

    companion object {
        const val TOOL_WINDOW_ID = "摸鱼工具"
    }
}
