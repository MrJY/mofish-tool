package online.mofish.tool.ui.web

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel

class MoFishWebEditor(
    private val project: Project,
    private val file: MoFishWebVirtualFile,
) : UserDataHolderBase(), FileEditor {
    private val browser: JBCefBrowser?
    private val component: JPanel = JPanel(BorderLayout())

    init {
        if (JBCefApp.isSupported()) {
            browser = JBCefBrowser()
            component.add(browser.component, BorderLayout.CENTER)
            when (val request = file.request) {
                is MoFishWebRequest.Url -> browser.loadURL(request.url)
                is MoFishWebRequest.Html -> browser.loadHTML(request.html)
            }
        } else {
            browser = null
            component.add(createUnsupportedPanel(project, file.request), BorderLayout.CENTER)
        }
    }

    /**
     * 获取组件。
     * @return 处理后的结果或当前状态。
     */
    override fun getComponent(): JComponent = component

    /**
     * 获取PreferredFocused组件。
     * @return 处理后的结果或当前状态。
     */
    override fun getPreferredFocusedComponent(): JComponent = browser?.component ?: component

    /**
     * 返回组件、列或文件类型的展示名称。
     * @return 处理后的结果或当前状态。
     */
    override fun getName(): String = file.request.title

    /**
     * 设置状态。
     * @param state 状态。
     * @return 处理后的结果或当前状态。
     */
    override fun setState(state: FileEditorState) = Unit

    /**
     * 获取指定项目的当前状态，必要时触发一次完整刷新。
     * @param level level。
     * @return 处理后的结果或当前状态。
     */
    override fun getState(level: FileEditorStateLevel): FileEditorState = FileEditorState.INSTANCE

    /**
     * 判断当前配置页内容是否相对持久化状态发生变化。
     * @return 处理后的结果或当前状态。
     */
    override fun isModified(): Boolean = false

    /**
     * 判断是否满足Valid条件。
     * @return 处理后的结果或当前状态。
     */
    override fun isValid(): Boolean = file.isValid

    /**
     * 添加PropertyChangeListener。
     * @param listener listener。
     * @return 处理后的结果或当前状态。
     */
    override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit

    /**
     * 删除PropertyChangeListener。
     * @param listener listener。
     * @return 处理后的结果或当前状态。
     */
    override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit

    /**
     * 获取当前Location。
     * @return 处理后的结果或当前状态。
     */
    override fun getCurrentLocation(): FileEditorLocation? = null

    /**
     * 获取文件。
     * @return 处理后的结果或当前状态。
     */
    override fun getFile(): VirtualFile = file

    /**
     * 释放服务持有的后台任务和运行资源。
     */
    override fun dispose() {
        browser?.dispose()
        file.invalidate()
    }

    /**
     * 获取User数据。
     * @param key key。
     * @return 处理后的结果或当前状态。
     */
    override fun <T : Any?> getUserData(key: Key<T>): T? = super.getUserData(key)

    /**
     * 处理 putUserData 相关逻辑，并返回调用方需要的结果。
     * @param key key。
     * @param value 待解析、格式化或写入的原始值。
     * @return 处理后的结果或当前状态。
     */
    override fun <T : Any?> putUserData(key: Key<T>, value: T?) = super.putUserData(key, value)

    /**
     * 创建Unsupported面板实例或展示内容。
     * @param project 当前 IntelliJ 项目实例。
     * @param request 当前数据请求或页面请求对象。
     * @return 处理后的结果或当前状态。
     */
    private fun createUnsupportedPanel(project: Project, request: MoFishWebRequest): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(16)
        val message = when (request) {
            is MoFishWebRequest.Url -> "当前 IDE 环境不支持 JCEF，无法在编辑器中显示网页。"
            is MoFishWebRequest.Html -> "当前 IDE 环境不支持 JCEF，无法在编辑器中显示 HTML 内容。"
        }
        panel.add(JBLabel("$message（${project.name}）"), BorderLayout.NORTH)
        if (request is MoFishWebRequest.Url) {
            panel.add(ActionLink("在外部浏览器打开") { BrowserUtil.browse(request.url) }, BorderLayout.CENTER)
        }
        return panel
    }
}
