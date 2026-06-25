package online.mofish.tool.ui.web

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project

object MoFishWebEditorService {
    /**
     * 处理 open 相关逻辑，并返回调用方需要的结果。
     * @param project 当前 IntelliJ 项目实例。
     * @param request 当前数据请求或页面请求对象。
     */
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

    /**
     * 处理 sanitizeTabName 相关逻辑，并返回调用方需要的结果。
     * @param title 通知、卡片或窗口标题。
     * @return 处理后的结果或当前状态。
     */
    private fun sanitizeTabName(title: String): String {
        return title
            .replace('/', '-')
            .replace('\\', '-')
            .ifBlank { "mofish网页" }
    }
}
