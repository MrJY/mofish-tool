package online.mofish.tool.ui.web

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project

object MoFishWebEditorService {
    fun open(project: Project, request: MoFishWebRequest) {
        // Trend pages are generated on demand and backed by in-memory virtual files. Opening a new
        // virtual file each time keeps stock/fund tabs independent even when the same symbol is
        // refreshed with different generated HTML.
        val file = MoFishWebVirtualFile(
            tabName = sanitizeTabName(request.title),
            request = request,
        )
        FileEditorManager.getInstance(project).openFile(file, true)
    }

    private fun sanitizeTabName(title: String): String {
        return title
            .replace('/', '-')
            .replace('\\', '-')
            .ifBlank { "摸鱼网页" }
    }
}
