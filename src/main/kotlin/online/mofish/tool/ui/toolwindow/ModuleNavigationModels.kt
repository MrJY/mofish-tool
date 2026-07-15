package online.mofish.tool.ui.toolwindow

import com.intellij.util.ui.JBUI
import online.mofish.tool.domain.MoFishRefreshModule

internal data class ModuleNavItem(
    val viewId: String,
    val displayName: String,
) {
    /**
     * 转换为String表示。
     * @return 处理后的结果或当前状态。
     */
    override fun toString(): String = displayName
}

internal val MODULE_NAV_WIDTH = JBUI.scale(120)
internal val MODULE_NAV_COLLAPSED_WIDTH = JBUI.scale(28)
internal const val GOMOKU_VIEW_ID = "gomoku"
internal val DEFAULT_MODULES = MoFishRefreshModule.visibleModules.map { module ->
    ModuleNavItem(module.viewId, module.toString())
} + ModuleNavItem(GOMOKU_VIEW_ID, "mofish5")
