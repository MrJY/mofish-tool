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
    /** 旧配置兼容项：快讯模块已移除，不再展示或刷新。 */
    @Deprecated("The flash news module has been removed; keep this entry only for persisted settings compatibility.")
    NEWS("news", "快讯"),
    ;

    /**
     * 转换为String表示。
     * @return 处理后的结果或当前状态。
     */
    override fun toString(): String = displayName

    companion object {
        /** 当前产品中可见的模块集合。 */
        val visibleModules: Set<MoFishRefreshModule> = setOf(
            STOCKS,
            INDICES,
            FUNDS,
            CRYPTO,
            FOREX,
        )

        /** 默认启用的模块集合，包含所有可见刷新模块。 */
        val defaultEnabledModules: Set<MoFishRefreshModule> = visibleModules

        /** 支持自动刷新的模块集合；基金和外汇仅保留手动刷新。 */
        val autoRefreshModules: Set<MoFishRefreshModule> = setOf(
            STOCKS,
            INDICES,
            CRYPTO,
        )

        /** 默认参与自动刷新的模块集合。 */
        val defaultAutoRefreshModules: Set<MoFishRefreshModule> = autoRefreshModules
    }
}
