package online.mofish.tool.ui.toolwindow

import com.intellij.util.ui.JBUI

internal data class ModuleNavItem(
    val viewId: String,
    val displayName: String,
) {
    override fun toString(): String = displayName
}

internal val MODULE_NAV_WIDTH = JBUI.scale(120)
internal val MODULE_NAV_COLLAPSED_WIDTH = JBUI.scale(28)
internal val DEFAULT_MODULES = listOf(
    ModuleNavItem("stocks", "摸鱼股票"),
    ModuleNavItem("indices", "摸鱼指数"),
    ModuleNavItem("funds", "摸鱼基金"),
    ModuleNavItem("crypto", "摸鱼虚拟币"),
    ModuleNavItem("forex", "摸鱼外汇"),
    ModuleNavItem("news", "快讯"),
)
