package online.mofish.tool.domain

enum class MoFishRefreshModule(
    /** 工具窗口中对应模块的视图 ID。 */
    val viewId: String,
    /** 模块在界面中展示的中文名称。 */
    private val displayName: String,
) {
    /** 股票行情模块。 */
    STOCKS("stocks", "摸鱼股票"),
    /** 市场指数模块。 */
    INDICES("indices", "摸鱼指数"),
    /** 基金行情模块。 */
    FUNDS("funds", "摸鱼基金"),
    /** 虚拟币行情模块。 */
    CRYPTO("crypto", "摸鱼虚拟币"),
    /** 外汇牌价模块。 */
    FOREX("forex", "摸鱼外汇"),
    /** 财经快讯模块。 */
    NEWS("news", "快讯"),
    ;

    /**
     * 转换为String表示。
     * @return 处理后的结果或当前状态。
     */
    override fun toString(): String = displayName

    companion object {
        /** 默认启用的模块集合，包含所有刷新模块。 */
        val defaultEnabledModules: Set<MoFishRefreshModule> = entries.toSet()

        /** 默认参与自动刷新的模块集合，排除快讯等非默认自动刷新内容。 */
        val defaultAutoRefreshModules: Set<MoFishRefreshModule> = setOf(
            STOCKS,
            INDICES,
            FUNDS,
            CRYPTO,
            FOREX,
        )
    }
}
