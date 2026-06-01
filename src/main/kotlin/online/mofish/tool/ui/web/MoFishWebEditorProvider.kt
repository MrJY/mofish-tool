package online.mofish.tool.ui.web

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class MoFishWebEditorProvider : FileEditorProvider, DumbAware {
    /**
     * 判断当前 provider 是否支持给定虚拟文件。
     * @param project 当前 IntelliJ 项目实例。
     * @param file 文件。
     * @return 处理后的结果或当前状态。
     */
    override fun accept(project: Project, file: VirtualFile): Boolean = file is MoFishWebVirtualFile

    /**
     * 为摸鱼虚拟网页文件创建对应的编辑器实例。
     * @param project 当前 IntelliJ 项目实例。
     * @param file 文件。
     * @return 处理后的结果或当前状态。
     */
    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        return MoFishWebEditor(project, file as MoFishWebVirtualFile)
    }

    /**
     * 获取编辑器TypeId。
     * @return 处理后的结果或当前状态。
     */
    override fun getEditorTypeId(): String = "mofish-web-editor"

    /**
     * 获取Policy。
     * @return 处理后的结果或当前状态。
     */
    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}
