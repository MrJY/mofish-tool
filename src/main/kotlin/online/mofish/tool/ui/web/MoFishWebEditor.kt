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

    override fun getComponent(): JComponent = component

    override fun getPreferredFocusedComponent(): JComponent = browser?.component ?: component

    override fun getName(): String = file.request.title

    override fun setState(state: FileEditorState) = Unit

    override fun getState(level: FileEditorStateLevel): FileEditorState = FileEditorState.INSTANCE

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = file.isValid

    override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun getCurrentLocation(): FileEditorLocation? = null

    override fun getFile(): VirtualFile = file

    override fun dispose() {
        browser?.dispose()
        file.invalidate()
    }

    override fun <T : Any?> getUserData(key: Key<T>): T? = super.getUserData(key)

    override fun <T : Any?> putUserData(key: Key<T>, value: T?) = super.putUserData(key, value)

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
