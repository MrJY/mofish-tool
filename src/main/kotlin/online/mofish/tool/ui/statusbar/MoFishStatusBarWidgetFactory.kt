package online.mofish.tool.ui.statusbar

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

class MoFishStatusBarWidgetFactory : StatusBarWidgetFactory {

    /**
     * 获取Id。
     * @return 处理后的结果或当前状态。
     */
    override fun getId(): String = MoFishStatusBarWidget.WIDGET_ID

    /**
     * 获取Display名称。
     * @return 处理后的结果或当前状态。
     */
    override fun getDisplayName(): String = "摸鱼工具行情"

    /**
     * 判断是否满足Available条件。
     * @param project 当前 IntelliJ 项目实例。
     * @return 处理后的结果或当前状态。
     */
    override fun isAvailable(project: Project): Boolean = true

    /**
     * 创建Widget实例或展示内容。
     * @param project 当前 IntelliJ 项目实例。
     * @return 处理后的结果或当前状态。
     */
    override fun createWidget(project: Project): StatusBarWidget = MoFishStatusBarWidget(project)

    /**
     * 处理 disposeWidget 相关逻辑，并返回调用方需要的结果。
     * @param widget widget。
     */
    override fun disposeWidget(widget: StatusBarWidget) {
        Disposer.dispose(widget)
    }

    /**
     * 判断当前上下文是否允许BeEnabledOn。
     * @param statusBar statusBar。
     * @return 处理后的结果或当前状态。
     */
    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}
