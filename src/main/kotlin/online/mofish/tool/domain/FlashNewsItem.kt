package online.mofish.tool.domain

import java.time.LocalDateTime

data class FlashNewsItem(
    /** 快讯唯一标识，用于去重和列表稳定渲染。 */
    val id: String,
    /** 快讯来源。 */
    val source: FlashNewsSource,
    /** 快讯标题。 */
    val title: String,
    /** 快讯摘要或正文短描述。 */
    val summary: String,
    /** 快讯发生或发布时间。 */
    val occurredAt: LocalDateTime,
    /** 快讯对市场情绪的影响方向。 */
    val impact: FlashNewsImpact = FlashNewsImpact.NEUTRAL,
    /** 是否为重要快讯。 */
    val important: Boolean = false,
    /** 快讯关联的板块名称列表。 */
    val relatedBoards: List<String> = emptyList(),
    /** 快讯关联的股票代码列表。 */
    val relatedStocks: List<String> = emptyList(),
    /** 快讯标签，用于分类筛选和展示。 */
    val tags: List<String> = emptyList(),
    /** 快讯原文链接，没有链接时为空。 */
    val url: String? = null,
)
