package online.mofish.tool.ui.web

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class MoFishWebEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean = file is MoFishWebVirtualFile

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        return MoFishWebEditor(project, file as MoFishWebVirtualFile)
    }

    override fun getEditorTypeId(): String = "mofish-web-editor"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}
