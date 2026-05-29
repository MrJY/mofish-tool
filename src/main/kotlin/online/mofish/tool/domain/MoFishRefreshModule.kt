package online.mofish.tool.domain

enum class MoFishRefreshModule(
    val viewId: String,
    private val displayName: String,
) {
    STOCKS("stocks", "摸鱼股票"),
    INDICES("indices", "摸鱼指数"),
    FUNDS("funds", "摸鱼基金"),
    CRYPTO("crypto", "摸鱼虚拟币"),
    FOREX("forex", "摸鱼外汇"),
    NEWS("news", "快讯"),
    ;

    override fun toString(): String = displayName

    companion object {
        val defaultAutoRefreshModules: Set<MoFishRefreshModule> = setOf(
            STOCKS,
            INDICES,
            FUNDS,
            CRYPTO,
            FOREX,
        )
    }
}
