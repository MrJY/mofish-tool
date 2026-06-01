package online.mofish.tool.ui.toolwindow

import com.intellij.util.ui.JBUI
import online.mofish.tool.domain.MoFishRefreshModule

internal data class ModuleNavItem(
    val viewId: String,
    val displayName: String,
) {
    override fun toString(): String = displayName
}

internal val MODULE_NAV_WIDTH = JBUI.scale(120)
internal val MODULE_NAV_COLLAPSED_WIDTH = JBUI.scale(28)
internal val DEFAULT_MODULES = MoFishRefreshModule.entries.map { module ->
    ModuleNavItem(module.viewId, module.toString())
}
